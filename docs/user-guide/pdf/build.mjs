// Builds the user guide of one language: Markdown -> HTML -> paged by Paged.js -> PDF printed by headless Edge/Chrome.
//
//   node pdf/build.mjs --lang fr            -> build/user-guide-fr-v<version>.pdf (and build/user-guide-fr.html)
//   node pdf/build.mjs --lang fr --html     -> only the HTML preview
//
// No Pandoc or LaTeX needed: Markdown is read by markdown-it, the pages (running headers, page numbers, the
// table of contents with page numbers) are made by Paged.js, and the PDF is printed by Edge or Chrome.
// {{string_key}} in the Markdown becomes the bold text of that string of the app, in the language of the guide.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import MarkdownIt from "markdown-it";
import attrs from "markdown-it-attrs";
import { resolveLabels } from "./ui-strings.mjs";
import { printToPdf } from "./print-pdf.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const guide = path.dirname(here);
const repo = path.dirname(path.dirname(guide));

const args = process.argv.slice(2);
const langIndex = args.indexOf("--lang");
const lang = langIndex >= 0 ? args[langIndex + 1] : "en";
const htmlOnly = args.includes("--html");

const meta = JSON.parse(fs.readFileSync(path.join(guide, "i18n", "meta.json"), "utf8"));
const m = meta[lang];
if (!m) throw new Error("unknown language " + lang);

function appVersion() {
  const gradle = fs.readFileSync(path.join(repo, "dev3-app", "build.gradle.kts"), "utf8");
  const found = /versionName\s*=\s*"([^"]+)"/.exec(gradle);
  return found ? found[1] : "0.0";
}
const version = appVersion();

const md = new MarkdownIt({ html: true, typographer: false }).use(attrs);
md.validateLink = () => true; // the images are file: addresses on the build machine
const contentDir = path.join(guide, "content", lang);
const files = fs.readdirSync(contentDir).filter((f) => /^\d\d-.*\.md$/.test(f)).sort();

let chapterNo = 0;
let sectionNo = 0;
const chapters = [];
const toc = [];
for (const file of files) {
  let source = fs.readFileSync(path.join(contentDir, file), "utf8");
  const resolved = resolveLabels(source, lang);
  if (resolved.missing.length) console.error("UNKNOWN LABEL KEYS in " + file + ": " + resolved.missing.join(", "));
  source = resolved.text;
  // images are written relative to the Markdown file; make them absolute for the HTML page
  source = source.replace(/\]\((\.\.\/\.\.\/screenshots\/[^)]+)\)/g, (_all, rel) => {
    return "](" + pathToFileURL(path.resolve(contentDir, rel)).href + ")";
  });
  let html = md.render(source);
  // a figure: an image paragraph followed by an emphasised caption paragraph
  html = html.replace(/<p>(<img [^>]*>)<\/p>\s*<p><em>([\s\S]*?)<\/em><\/p>/g, (_all, img, caption) => {
    return "<figure>" + img + "<figcaption>" + caption + "</figcaption></figure>";
  });
  // numbered headings; the "template" headings of a chapter stay out of the contents
  html = html.replace(/<h([12])([^>]*)>([\s\S]*?)<\/h\1>/g, (all, level, attributes, inner) => {
    const id = /id="([^"]+)"/.exec(attributes);
    const unnumbered = /unnumbered/.test(attributes);
    let number = "";
    if (!unnumbered) {
      if (level === "1") {
        chapterNo += 1;
        sectionNo = 0;
        number = String(chapterNo);
      } else {
        sectionNo += 1;
        number = chapterNo + "." + sectionNo;
      }
    }
    const text = inner.replace(/<[^>]+>/g, "");
    const template = id && /-(purpose|before|steps|tips|problems)$/.test(id[1]);
    if (id && !template) toc.push({ level: Number(level), id: id[1], text, number });
    const prefix = number ? `<span class="num">${number}</span> ` : "";
    return `<h${level}${attributes}>${prefix}${inner}</h${level}>`;
  });
  chapters.push(html);
}

const tocHtml = toc
  .map(
    (t) =>
      `<li class="toc-${t.level}"><a href="#${t.id}"><span class="toc-text">${t.number ? t.number + " " : ""}${t.text}</span></a></li>`,
  )
  .join("\n");

const date = new Date().toISOString().slice(0, 10);
const css = fs.readFileSync(path.join(here, "guide.css"), "utf8");

const page = `<!doctype html>
<html lang="${lang}" dir="ltr" class="${m.dir}">
<head>
<meta charset="utf-8">
<title>${m.title}</title>
<style>
${css}
html { string-set: apptitle "${m.runningHeader}"; }
</style>
</head>
<body>
<section class="cover">
  <p class="cover-kicker">${m.kicker}</p>
  <div class="cover-title">${m.title}</div>
  <p class="cover-sub">${m.subtitle}</p>
  <p class="cover-meta">${m.versionLabel} <bdi dir="ltr">${version} · ${date}</bdi></p>
  <p class="cover-audience">${m.audience}</p>
</section>
<nav class="toc" id="toc">
  <div class="toc-title">${m.tocTitle}</div>
  <ol>
${tocHtml}
  </ol>
</nav>
<main>
${chapters.join("\n")}
</main>
<script>
window.PagedConfig = { auto: true, after: function () { window.__guideDone = true; } };
</script>
<script src="${pathToFileURL(path.join(here, "node_modules", "pagedjs", "dist", "paged.polyfill.js")).href}"></script>
</body>
</html>`;

const buildDir = path.join(guide, "build");
fs.mkdirSync(buildDir, { recursive: true });
const htmlFile = path.join(buildDir, `user-guide-${lang}.html`);
fs.writeFileSync(htmlFile, page, "utf8");
console.log("wrote", path.relative(repo, htmlFile));
if (htmlOnly) process.exit(0);

const pdf = path.join(buildDir, `user-guide-${lang}-v${version}.pdf`);
if (fs.existsSync(pdf)) fs.unlinkSync(pdf);
const pages = await printToPdf(pathToFileURL(htmlFile).href, pdf);
console.log("wrote", path.relative(repo, pdf), Math.round(fs.statSync(pdf).size / 1024) + " KB,", pages, "pages");
