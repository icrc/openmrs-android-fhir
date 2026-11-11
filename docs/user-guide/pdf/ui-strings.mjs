// Reads the app's strings.xml of a language, so the guide can use the app's own words.
// In the Markdown, {{string_key}} becomes **the text of that string** in the language of the guide.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.dirname(path.dirname(path.dirname(here)));
const res = path.join(repo, "core", "src", "main", "res");

function unescapeAndroid(text) {
  return text
    .replace(/\\'/g, "'")
    .replace(/\\"/g, '"')
    .replace(/\\n/g, " ")
    .replace(/\\@/g, "@")
    .replace(/\\\?/g, "?")
    .replace(/<[^>]+>/g, "")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ")
    .trim();
}

const cache = {};
export function loadStrings(lang) {
  if (cache[lang]) return cache[lang];
  const folder = lang === "en" ? "values" : "values-" + lang;
  const xml = fs.readFileSync(path.join(res, folder, "strings.xml"), "utf8");
  const map = {};
  const re = /<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g;
  let found;
  while ((found = re.exec(xml))) map[found[1]] = unescapeAndroid(found[2]);
  if (lang !== "en") {
    const english = loadStrings("en");
    for (const k of Object.keys(english)) if (!(k in map)) map[k] = english[k];
  }
  cache[lang] = map;
  return map;
}

/** Replaces {{key}} by the bold text of the app. Returns the text and the keys that do not exist. */
export function resolveLabels(markdown, lang) {
  const strings = loadStrings(lang);
  const missing = [];
  const out = markdown.replace(/\{\{([a-z0-9_]+)\}\}/g, (_all, key) => {
    if (!(key in strings)) {
      missing.push(key);
      return "**?" + key + "?**";
    }
    return "**" + strings[key] + "**";
  });
  return { text: out, missing };
}
