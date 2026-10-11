// The Node.js that runs inside the Android app (nodejs-mobile) is built without ICU, so a few things
// the bot relies on are missing: Intl (time zones for !ping/!help/!weather) and TextDecoder's "fatal" option.
// This file adds small stand-ins. It does nothing on a normal Node.js that has ICU.
"use strict";
const util = require("util");

// ---- TextDecoder({ fatal: true }) ---------------------------------------------------------------
(function fixTextDecoder() {
  const Native = global.TextDecoder;
  if (!Native) return;
  try {
    new Native("utf-8", { fatal: true });
    return; // works, nothing to do
  } catch (e) {
    if (!e || e.code !== "ERR_NO_ICU") return;
  }
  class TextDecoder extends Native {
    constructor(encoding, options) {
      const o = Object.assign({}, options);
      delete o.fatal;
      super(encoding, o);
    }
  }
  global.TextDecoder = TextDecoder;
  util.TextDecoder = TextDecoder;
})();

// ---- Intl.DateTimeFormat (English only) ----------------------------------------------------------
const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
const US_ABBR = { EST: 1, EDT: 1, CST: 1, CDT: 1, MST: 1, MDT: 1, PST: 1, PDT: 1, AKST: 1, AKDT: 1, HST: 1 };
const pad2 = (n) => String(n).padStart(2, "0");

let momentTz = null;
function zoneOf(name) {
  if (/^(utc|etc\/utc|gmt|etc\/gmt)$/i.test(name)) return { utcOffset: () => 0, abbr: () => "UTC" };
  if (!momentTz) momentTz = require("moment-timezone");
  const zone = momentTz.tz.zone(name);
  if (!zone) throw new RangeError("Invalid time zone specified: " + name);
  return zone;
}

function localFields(ms, zoneName) {
  let offsetMin;
  let abbr = null;
  if (zoneName) {
    const z = zoneOf(zoneName);
    offsetMin = -z.utcOffset(ms);
    abbr = z.abbr(ms);
  } else {
    offsetMin = -new Date(ms).getTimezoneOffset();
  }
  const d = new Date(ms + offsetMin * 60000);
  return {
    y: d.getUTCFullYear(), mo: d.getUTCMonth(), day: d.getUTCDate(), wd: d.getUTCDay(),
    h: d.getUTCHours(), mi: d.getUTCMinutes(), s: d.getUTCSeconds(), offsetMin, abbr,
  };
}

function shortZoneName(f, zoneName) {
  if (zoneName && /^(utc|etc\/utc)$/i.test(zoneName)) return "UTC";
  if (f.abbr && US_ABBR[f.abbr]) return f.abbr;
  if (f.offsetMin === 0) return "GMT";
  const a = Math.abs(f.offsetMin);
  return "GMT" + (f.offsetMin > 0 ? "+" : "-") + Math.floor(a / 60) + (a % 60 ? ":" + pad2(a % 60) : "");
}

class DateTimeFormat {
  constructor(locales, options) {
    const o = Object.assign({}, options);
    this._locale = (Array.isArray(locales) ? locales[0] : locales) || "en-US";
    this._tz = o.timeZone;
    if (this._tz !== undefined) zoneOf(this._tz); // throws RangeError for a bad name, like the real one
    if (!o.weekday && !o.year && !o.month && !o.day && !o.hour && !o.minute && !o.second && !o.timeZoneName) {
      o.year = "numeric"; o.month = "numeric"; o.day = "numeric";
    }
    this._o = o;
  }

  formatToParts(date) {
    const ms = date === undefined ? Date.now() : date instanceof Date ? date.getTime() : Number(date);
    if (!Number.isFinite(ms)) throw new RangeError("Invalid time value");
    const o = this._o;
    const f = localFields(ms, this._tz);
    const parts = [];
    const add = (type, value) => parts.push({ type, value: String(value) });
    const lit = (value) => parts.push({ type: "literal", value });

    // --- date
    const textMonth = o.month === "short" || o.month === "long" || o.month === "narrow";
    const monthText = o.month === "long" ? MONTHS[f.mo] : MONTHS[f.mo].slice(0, 3);
    const yearText = o.year === "2-digit" ? pad2(f.y % 100) : String(f.y);
    const dayText = o.day === "2-digit" ? pad2(f.day) : String(f.day);
    const isCA = /^en-CA/i.test(this._locale);
    const hasDate = o.weekday || o.year || o.month || o.day;

    if (o.weekday) {
      add("weekday", o.weekday === "long" ? WEEKDAYS[f.wd] : WEEKDAYS[f.wd].slice(0, 3));
      if (o.year || o.month || o.day) lit(", ");
    }
    if (textMonth) {
      if (o.month) add("month", monthText);
      if (o.day) { if (o.month) lit(" "); add("day", dayText); }
      if (o.year) { if (o.month || o.day) lit(o.day ? ", " : " "); add("year", yearText); }
    } else if (isCA && o.year && o.month && o.day) {
      add("year", yearText); lit("-"); add("month", pad2(f.mo + 1)); lit("-"); add("day", pad2(f.day));
    } else {
      const seq = [];
      if (o.month) seq.push(["month", o.month === "2-digit" ? pad2(f.mo + 1) : String(f.mo + 1)]);
      if (o.day) seq.push(["day", dayText]);
      if (o.year) seq.push(["year", yearText]);
      seq.forEach((p, i) => { if (i) lit("/"); add(p[0], p[1]); });
    }

    // --- time
    if (o.hour || o.minute || o.second) {
      if (hasDate) lit(", ");
      const hour12 = o.hour12 === undefined ? o.hourCycle !== "h23" : o.hour12;
      if (o.hour) {
        const h = hour12 ? f.h % 12 || 12 : f.h;
        add("hour", o.hour === "2-digit" || (!hour12 && o.hour) ? pad2(h) : h);
      }
      if (o.minute) { if (o.hour) lit(":"); add("minute", o.hour || o.minute === "2-digit" ? pad2(f.mi) : f.mi); }
      if (o.second) { if (o.hour || o.minute) lit(":"); add("second", o.hour || o.minute || o.second === "2-digit" ? pad2(f.s) : f.s); }
      if (o.hour && hour12) { lit(" "); add("dayPeriod", f.h < 12 ? "AM" : "PM"); }
    }

    if (o.timeZoneName) {
      if (parts.length) lit(" ");
      add("timeZoneName", shortZoneName(f, this._tz));
    }
    return parts;
  }

  format(date) {
    return this.formatToParts(date).map((p) => p.value).join("");
  }

  resolvedOptions() {
    return { locale: this._locale, calendar: "gregory", numberingSystem: "latn", timeZone: this._tz || process.env.TZ || "UTC" };
  }

  static supportedLocalesOf(l) { return Array.isArray(l) ? l : l ? [l] : []; }
}

// ---- tiny stand-ins so code that only checks "is Intl there?" keeps working ----------------------
class NumberFormat {
  constructor(locales, options) { this._o = Object.assign({}, options); }
  format(n) {
    const max = this._o.maximumFractionDigits !== undefined ? this._o.maximumFractionDigits : 3;
    const min = this._o.minimumFractionDigits || 0;
    let s = Number(n).toFixed(Math.max(max, min));
    if (s.includes(".")) {
      let [i, d] = s.split(".");
      d = d.replace(/0+$/, "");
      while (d.length < min) d += "0";
      s = d ? i + "." + d : i;
    }
    return s.replace(/\B(?=(\d{3})+(?!\d))/g, (m, off, str) => (str.indexOf(".") !== -1 && off > str.indexOf(".") ? "" : ","));
  }
  resolvedOptions() { return { locale: "en-US", numberingSystem: "latn" }; }
}
class Collator {
  constructor(locales, options) { this._base = options && (options.sensitivity === "base" || options.sensitivity === "accent"); }
  compare(a, b) { a = String(a); b = String(b); if (this._base) { a = a.toLowerCase(); b = b.toLowerCase(); } return a < b ? -1 : a > b ? 1 : 0; }
}
class PluralRules {
  select(n) { return n === 1 ? "one" : "other"; }
}

function install() {
  if (typeof Intl !== "undefined" && typeof Intl.DateTimeFormat === "function") return false; // real Intl present
  global.Intl = { DateTimeFormat, NumberFormat, Collator, PluralRules, getCanonicalLocales: (l) => (Array.isArray(l) ? l : l ? [l] : []) };

  const format = (date, locales, options, kind) => {
    if (isNaN(date.getTime())) return "Invalid Date";
    const o = Object.assign({}, options);
    const hasDate = o.weekday || o.year || o.month || o.day;
    const hasTime = o.hour || o.minute || o.second;
    const wantDate = kind !== "time" && !hasDate && !(kind === "both" && hasTime);
    const wantTime = kind !== "date" && !hasTime && !(kind === "both" && hasDate);
    if (kind === "date" ? !hasDate : kind === "time" ? false : !hasDate && !hasTime) { o.year = "numeric"; o.month = "numeric"; o.day = "numeric"; }
    if (kind === "time" ? !hasTime : kind === "date" ? false : !hasDate && !hasTime) { o.hour = "numeric"; o.minute = "2-digit"; o.second = "2-digit"; }
    void wantDate; void wantTime;
    return new DateTimeFormat(locales, o).format(date);
  };
  Date.prototype.toLocaleDateString = function (locales, options) { return format(this, locales, options, "date"); };
  Date.prototype.toLocaleTimeString = function (locales, options) { return format(this, locales, options, "time"); };
  Date.prototype.toLocaleString = function (locales, options) { return format(this, locales, options, "both"); };
  return true;
}

install();
module.exports = { install, DateTimeFormat };
