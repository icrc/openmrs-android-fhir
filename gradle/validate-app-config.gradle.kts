import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// Checks `res/xml/app_config.xml` of the module the build is for, so a typo is found when the app is
// built and not when it starts (AppConfig reads the same file on the device). Mistakes fail the
// build; things that work but are probably not meant (the example diagnostics password) are warned
// about.
val appConfigFile = layout.projectDirectory.file("src/main/res/xml/app_config.xml").asFile

val validateAppConfig =
  tasks.register("validateAppConfig") {
    group = "verification"
    description = "Checks res/xml/app_config.xml: required settings, values and duplicates."
    inputs.files(appConfigFile).optional()
    onlyIf { appConfigFile.exists() }
    doLast {
      val problems = mutableListOf<String>()
      val warnings = mutableListOf<String>()
      val document =
        try {
          DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(appConfigFile)
        } catch (e: Exception) {
          throw GradleException("${appConfigFile.path}: not well-formed XML (${e.message})")
        }
      val root = document.documentElement

      fun elements(tag: String) =
        (0 until root.getElementsByTagName(tag).length).map {
          root.getElementsByTagName(tag).item(it) as Element
        }

      fun single(tag: String): Element? = elements(tag).firstOrNull()

      val serverUrl = single("server")?.getAttribute("url").orEmpty()
      when {
        serverUrl.isBlank() -> problems += "<server url=\"...\"/> is required"
        !Regex("^https?://[^\\s/]+.*").matches(serverUrl) ->
          problems += "<server url> must start with http:// or https:// (is \"$serverUrl\")"
      }

      single("auth")?.getAttribute("method")?.takeIf { it.isNotEmpty() }?.let {
        if (it !in setOf("basic", "openid", "both")) {
          problems += "<auth method> must be basic, openid or both (is \"$it\")"
        }
      }

      val password = single("diagnostics")?.getAttribute("password").orEmpty()
      when {
        password.isBlank() -> problems += "<diagnostics password=\"...\"/> is required"
        password.equals("changeit", ignoreCase = true) ->
          warnings += "<diagnostics password> is still the example value: change it before release"
      }

      single("support")?.getAttribute("email")?.takeIf { it.isNotEmpty() }?.let {
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(it)) {
          problems += "<support email> is not an e-mail address (is \"$it\")"
        }
      }

      single("audit")?.getAttribute("max-actions")?.takeIf { it.isNotEmpty() }?.let {
        val value = it.toIntOrNull()
        if (value == null || value !in 50..5000) {
          warnings += "<audit max-actions> should be a number from 50 to 5000 (is \"$it\")"
        }
      }

      elements("section").forEach { section ->
        val name = section.getAttribute("name")
        if (name.isBlank()) problems += "a <section> has no name"
        val codes =
          (0 until section.getElementsByTagName("form").length).map {
            (section.getElementsByTagName("form").item(it) as Element).getAttribute("code")
          }
        if (codes.isEmpty()) warnings += "section \"$name\" has no <form code=\"...\"/>"
        if (codes.any { it.isBlank() }) problems += "section \"$name\" has a <form> without code"
        codes
          .filter { it.isNotBlank() }
          .groupBy { it }
          .filterValues { it.size > 1 }
          .keys
          .forEach { warnings += "form $it is listed twice in section \"$name\"" }
      }

      warnings.forEach { logger.warn("w: ${project.name} app_config.xml: $it") }
      if (problems.isNotEmpty()) {
        throw GradleException(
          "${project.name}: app_config.xml has errors:\n" + problems.joinToString("\n") { " - $it" },
        )
      }
    }
  }

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(validateAppConfig) }
