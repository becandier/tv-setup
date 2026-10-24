"""tools/prayer_table.py and the bundled tables in prayer-tables/ (the launcher's prayer_times)."""
import json
import os
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import prayer_table  # noqa: E402

TABLES = os.path.join(ROOT, "prayer-tables")


def minutes(clock):
    h, m = clock.split(":")
    return int(h) * 60 + int(m)


def load(name):
    with open(os.path.join(TABLES, name), encoding="utf-8") as f:
        return json.load(f)


class BundledTablesTest(unittest.TestCase):
    def test_every_index_entry_has_a_valid_table(self):
        index = load("index.json")
        self.assertGreaterEqual(len(index), 27)
        for slug, entry in index.items():
            with self.subTest(city=slug):
                self.assertTrue(entry["name"])
                self.assertTrue(entry["tz"])
                rows = load(slug + ".json")
                self.assertEqual(365, len(rows))
                for row in rows:
                    self.assertEqual(prayer_table.KEYS, list(row))
                    times = [minutes(row[k]) for k in prayer_table.KEYS]
                    self.assertEqual(sorted(times), times)

    def test_every_table_file_is_in_the_index(self):
        index = load("index.json")
        for name in os.listdir(TABLES):
            if name.endswith(".json") and name != "index.json":
                self.assertIn(name[:-5], index)


class GeneratorTest(unittest.TestCase):
    """Against the Dagestan muftiate's own table for Makhachkala, at the same coordinates."""

    @classmethod
    def setUpClass(cls):
        city = load("index.json")["makhachkala"]
        cls.lat, cls.lon = city["lat"], city["lon"]
        cls.ref = load("makhachkala.json")

    def worst(self, method):
        rows = prayer_table.table(self.lat, self.lon, "Europe/Moscow", method)
        return {k: max(abs(minutes(g[k]) - minutes(r[k])) for g, r in zip(rows, self.ref)) for k in prayer_table.KEYS}

    def test_the_dagestan_method_matches_the_muftiate_within_two_minutes(self):
        for prayer, worst in self.worst("dagestan").items():
            with self.subTest(prayer=prayer):
                self.assertLessEqual(worst, 2)

    def test_mwl_differs_by_its_angles_only(self):
        # Muslim World League puts isha at 17 degrees, the muftiate at 15 (and adds minutes): isha up to ~21 min
        # later, fajr and the rest within 6. Documented in docs/tv-setup/prayer-times.md.
        worst = self.worst("mwl")
        self.assertLessEqual(worst["isha"], 25)
        for prayer in ("fajr", "sunrise", "dhuhr", "asr", "maghrib"):
            self.assertLessEqual(worst[prayer], 7, prayer)

    def test_umm_al_qura_isha_is_90_minutes_after_maghrib(self):
        for row in prayer_table.table(self.lat, self.lon, "Europe/Moscow", "umm-al-qura"):
            self.assertEqual(90, minutes(row["isha"]) - minutes(row["maghrib"]))

    def test_hanafi_asr_is_later(self):
        shafii = prayer_table.table(self.lat, self.lon, "Europe/Moscow", "mwl", "shafii")
        hanafi = prayer_table.table(self.lat, self.lon, "Europe/Moscow", "mwl", "hanafi")
        self.assertTrue(all(minutes(h["asr"]) > minutes(s["asr"]) for h, s in zip(hanafi, shafii)))

    def test_high_latitude_stays_in_order_and_polar_day_is_refused(self):
        rows = prayer_table.table(59.9386, 30.3141, "Europe/Moscow", "mwl")   # Saint Petersburg, white nights
        self.assertEqual(365, len(rows))
        with self.assertRaises(ValueError):
            prayer_table.table(68.9585, 33.0827, "Europe/Moscow", "mwl")       # Murmansk, polar day

    def test_writes_the_table_and_the_index(self):
        with tempfile.TemporaryDirectory() as out:
            prayer_table.main(["--city", "kazan", "--name", "Казань", "--lat", "55.7963", "--lon", "49.1088",
                               "--tz", "Europe/Moscow", "--out", out])
            with open(os.path.join(out, "index.json"), encoding="utf-8") as f:
                index = json.load(f)
            self.assertEqual("Казань", index["kazan"]["name"])
            self.assertEqual("computed:mwl:shafii", index["kazan"]["source"])
            with open(os.path.join(out, "kazan.json"), encoding="utf-8") as f:
                self.assertEqual(365, len(json.load(f)))


if __name__ == "__main__":
    unittest.main()
