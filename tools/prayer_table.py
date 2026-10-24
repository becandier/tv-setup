#!/usr/bin/env python3
"""Prayer times for a city, offline: 365 rows of {fajr, sunrise, dhuhr, asr, maghrib, isha} as "HH:MM", the format of
the tables in prayer-tables/ that the launcher's prayer_times reads (row 0 is 1 January of a non-leap year; the
launcher gives 29 February the row of the 28th).

The standard astronomical method (the one PrayTimes.org describes): the sun's declination and the equation of time
for each day, fajr and isha at a depression angle below the horizon, sunrise and maghrib at 0.833 degrees, dhuhr at
solar noon, asr when a shadow is one (Shafii) or two (Hanafi) object lengths plus its noon shadow. No network.

Usage:
  tools/prayer_table.py --city kazan --name "Казань" --lat 55.7963 --lon 49.1088 --tz Europe/Moscow \\
      [--method mwl|isna|egypt|umm-al-qura|russia|dagestan] [--asr shafii|hanafi] [--out prayer-tables]

Writes <out>/<city>.json and adds the city to <out>/index.json, so `PRAYER_CITY=<city>` picks it at build time.
With --stdout it prints the table instead. Times are local wall time in --tz, as on that date of 2025.
"""
import argparse
import datetime
import json
import math
import os
import sys
from zoneinfo import ZoneInfo

# Depression angles in degrees; an isha given in minutes is that long after maghrib. "offsets" are minutes added
# after the computation, as some authorities publish them.
METHODS = {
    "mwl": {"fajr": 18.0, "isha": 17.0, "label": "Muslim World League"},
    "isna": {"fajr": 15.0, "isha": 15.0, "label": "ISNA (North America)"},
    "egypt": {"fajr": 19.5, "isha": 17.5, "label": "Egyptian General Authority of Survey"},
    "umm-al-qura": {"fajr": 18.5, "isha_minutes": 90, "label": "Umm al-Qura, Makkah"},
    "russia": {"fajr": 16.0, "isha": 15.0, "label": "ДУМ РФ (Spiritual Administration of Muslims of Russia)"},
    # The Dagestan muftiate's tables, fitted on Makhachkala's: fajr 17.5, isha 15, Shafii asr, then minutes added.
    "dagestan": {"fajr": 17.5, "isha": 15.0, "label": "Муфтият Дагестана (подобрано по таблице Махачкалы)",
                 "offsets": {"sunrise": -2, "dhuhr": 5, "asr": 2, "maghrib": 4}},
}
KEYS = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"]
REFERENCE_YEAR = 2025   # not a leap year: 365 rows


def _sin(d): return math.sin(math.radians(d))
def _cos(d): return math.cos(math.radians(d))
def _tan(d): return math.tan(math.radians(d))
def _asin(x): return math.degrees(math.asin(x))
def _acos(x): return math.degrees(math.acos(x))
def _atan2(y, x): return math.degrees(math.atan2(y, x))
def _acot(x): return math.degrees(math.atan(1.0 / x))
def _fix(a, b): return a - b * math.floor(a / b)


def _julian(date):
    y, m, d = date.year, date.month, date.day
    if m <= 2:
        y -= 1
        m += 12
    a = math.floor(y / 100)
    b = 2 - a + math.floor(a / 4)
    return math.floor(365.25 * (y + 4716)) + math.floor(30.6001 * (m + 1)) + d + b - 1524.5


def _sun(jd):
    """(declination, equation of time in hours) for a Julian day."""
    d = jd - 2451545.0
    g = _fix(357.529 + 0.98560028 * d, 360)
    q = _fix(280.459 + 0.98564736 * d, 360)
    lng = _fix(q + 1.915 * _sin(g) + 0.020 * _sin(2 * g), 360)
    e = 23.439 - 0.00000036 * d
    ra = _fix(_atan2(_cos(e) * _sin(lng), _cos(lng)) / 15, 24)
    return _asin(_sin(e) * _sin(lng)), q / 15 - ra


class _Day:
    def __init__(self, date, lat, lon):
        self.lat = lat
        self.jd = _julian(date) - lon / (15 * 24)

    def noon(self, t):
        return _fix(12 - _sun(self.jd + t)[1], 24)

    def at_angle(self, angle, t, before_noon):
        """Hours (local mean time of the meridian) when the sun is `angle` degrees below the horizon; nan if never."""
        decl = _sun(self.jd + t)[0]
        x = (-_sin(angle) - _sin(decl) * _sin(self.lat)) / (_cos(decl) * _cos(self.lat))
        if x < -1 or x > 1:
            return float("nan")
        h = _acos(x) / 15
        noon = self.noon(t)
        return noon - h if before_noon else noon + h

    def asr(self, factor, t):
        decl = _sun(self.jd + t)[0]
        angle = -_acot(factor + _tan(abs(self.lat - decl)))
        return self.at_angle(angle, t, False)


def day_times(date, lat, lon, tz, method, asr_factor):
    """Local times in hours for one date, before rounding."""
    m = METHODS[method]
    day = _Day(date, lat, lon)
    # One refinement pass from rough guesses (fraction of a day), as PrayTimes does.
    guess = {"fajr": 5, "sunrise": 6, "dhuhr": 12, "asr": 13, "sunset": 18, "isha": 18}
    t = {k: v / 24 for k, v in guess.items()}
    times = {
        "fajr": day.at_angle(m["fajr"], t["fajr"], True),
        "sunrise": day.at_angle(0.833, t["sunrise"], True),
        "dhuhr": day.noon(t["dhuhr"]),
        "asr": day.asr(asr_factor, t["asr"]),
        "maghrib": day.at_angle(0.833, t["sunset"], False),
        "isha": day.at_angle(m["isha"], t["isha"], False) if "isha" in m else float("nan"),
    }
    if math.isnan(times["sunrise"]) or math.isnan(times["maghrib"]) or math.isnan(times["asr"]):
        raise ValueError("%s: no sunrise or sunset (polar day or night); this table cannot describe it" % date)
    # High latitudes: when the sun never gets that low, or fajr/isha fall too far into the night, use the
    # angle-based portion of the night (angle / 60), the PrayTimes default.
    night = 24 - (times["maghrib"] - times["sunrise"])
    fajr_cap = times["sunrise"] - m["fajr"] / 60 * night
    if math.isnan(times["fajr"]) or times["sunrise"] - times["fajr"] > m["fajr"] / 60 * night:
        times["fajr"] = fajr_cap
    if "isha_minutes" in m:
        times["isha"] = times["maghrib"] + m["isha_minutes"] / 60
    else:
        isha_cap = times["maghrib"] + m["isha"] / 60 * night
        if math.isnan(times["isha"]) or times["isha"] - times["maghrib"] > m["isha"] / 60 * night:
            times["isha"] = isha_cap
    # From the meridian's mean time to the zone's wall clock on that date (DST included).
    noon_local = datetime.datetime(date.year, date.month, date.day, 12, tzinfo=ZoneInfo(tz))
    offset = noon_local.utcoffset().total_seconds() / 3600
    out = {}
    for k in KEYS:
        out[k] = times[k] + offset - lon / 15 + m.get("offsets", {}).get(k, 0) / 60
    return out


def _clock(hours):
    minutes = int(math.floor(hours * 60 + 0.5))
    minutes = max(0, min(minutes, 23 * 60 + 59))   # a time past midnight would break the day's order
    return "%02d:%02d" % (minutes // 60, minutes % 60)


def table(lat, lon, tz, method="mwl", asr="shafii"):
    if method not in METHODS:
        raise ValueError("unknown method %r; one of %s" % (method, ", ".join(METHODS)))
    factor = {"shafii": 1, "hanafi": 2}[asr]
    rows = []
    day = datetime.date(REFERENCE_YEAR, 1, 1)
    for _ in range(365):
        t = day_times(day, lat, lon, tz, method, factor)
        row = {k: _clock(t[k]) for k in KEYS}
        if not all(row[a] < row[b] for a, b in zip(KEYS, KEYS[1:])):
            raise ValueError("%s: times out of order %s (latitude too high for this method?)" % (day, row))
        rows.append(row)
        day += datetime.timedelta(days=1)
    return rows


def main(argv=None):
    p = argparse.ArgumentParser(description="Offline prayer-time table for the launcher's prayer_times.")
    p.add_argument("--city", required=True, help="slug, latin letters, digits and dashes: kazan, moscow, ufa")
    p.add_argument("--name", help="the city's name as people read it (index.json); default the slug")
    p.add_argument("--lat", type=float, required=True)
    p.add_argument("--lon", type=float, required=True)
    p.add_argument("--tz", required=True, help="IANA zone, e.g. Europe/Moscow")
    p.add_argument("--method", default="mwl", choices=sorted(METHODS))
    p.add_argument("--asr", default="shafii", choices=["shafii", "hanafi"])
    p.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "prayer-tables"))
    p.add_argument("--stdout", action="store_true", help="print the table, write nothing")
    a = p.parse_args(argv)
    if not all(c.isalnum() and c.isascii() or c == "-" for c in a.city) or not a.city:
        p.error("--city must be latin letters, digits and dashes")
    ZoneInfo(a.tz)   # fails early on a zone that does not exist
    rows = table(a.lat, a.lon, a.tz, a.method, a.asr)
    text = json.dumps(rows, indent=2) + "\n"
    if a.stdout:
        sys.stdout.write(text)
        return 0
    os.makedirs(a.out, exist_ok=True)
    with open(os.path.join(a.out, a.city + ".json"), "w", encoding="utf-8") as f:
        f.write(text)
    index_path = os.path.join(a.out, "index.json")
    index = {}
    if os.path.exists(index_path):
        with open(index_path, encoding="utf-8") as f:
            index = json.load(f)
    index[a.city] = {"name": a.name or a.city, "lat": a.lat, "lon": a.lon, "tz": a.tz,
                     "source": "computed:%s:%s" % (a.method, a.asr)}
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(dict(sorted(index.items())), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote %s.json (%s, asr %s); build with PRAYER_CITY=%s" % (a.city, METHODS[a.method]["label"], a.asr, a.city))
    return 0


if __name__ == "__main__":
    sys.exit(main())
