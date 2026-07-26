import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import patch

import main


def match(date: str, home: str = "Home", away: str = "Away") -> dict:
    return {
        "date": date,
        "league": "League",
        "home": home,
        "away": away,
        "time": "20:00",
        "channels": ["Channel"]
    }


class DailyCacheTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_cache_dir = main.CACHE_DIR
        main.CACHE_DIR = Path(self.temp_dir.name)
        main.cache.clear()

    def tearDown(self) -> None:
        main.CACHE_DIR = self.previous_cache_dir
        main.cache.clear()
        self.temp_dir.cleanup()

    def test_each_date_is_written_to_its_own_json(self) -> None:
        main.save_scraped_dates([
            match("2026-07-26"),
            match("2026-07-27", "Second Home", "Second Away")
        ])

        self.assertTrue((main.CACHE_DIR / "2026-07-26.json").exists())
        self.assertTrue((main.CACHE_DIR / "2026-07-27.json").exists())
        self.assertEqual(
            main.load_cache_from_disk("2026-07-27")["data"][0]["home"],
            "Second Home"
        )

    def test_fresh_date_does_not_scrape_again(self) -> None:
        expected = [match("2026-07-26")]
        main.save_cache_to_disk("2026-07-26", expected)

        with patch.object(main, "scrape_schedule_for_date") as scraper:
            actual = main.get_matches_cached("2026-07-26")

        self.assertEqual(actual, expected)
        scraper.assert_not_called()

    def test_old_long_lived_empty_cache_is_retried(self) -> None:
        main.cache["2026-07-28"] = {
            "data": [],
            "expiry": datetime.now() + timedelta(minutes=10)
        }
        expected = [match("2026-07-28")]

        with patch.object(
            main,
            "scrape_schedule_for_date",
            return_value=expected
        ) as scraper:
            actual = main.get_matches_cached("2026-07-28")

        self.assertEqual(actual, expected)
        scraper.assert_called_once()

    def test_scrape_persists_every_returned_date(self) -> None:
        scraped = [
            match("2026-07-26"),
            match("2026-07-28", "Future Home", "Future Away")
        ]
        with patch.object(main, "scrape_schedule_for_date", return_value=scraped):
            actual = main.get_matches_cached("2026-07-28")

        self.assertEqual(actual[0]["home"], "Future Home")
        self.assertTrue((main.CACHE_DIR / "2026-07-26.json").exists())
        self.assertTrue((main.CACHE_DIR / "2026-07-28.json").exists())

    def test_stale_date_is_kept_when_scraping_fails(self) -> None:
        expected = [match("2026-07-26")]
        main.cache["2026-07-26"] = {
            "data": expected,
            "expiry": datetime.now() - timedelta(minutes=1)
        }

        with patch.object(
            main,
            "scrape_schedule_for_date",
            side_effect=RuntimeError("offline")
        ):
            actual = main.get_matches_cached("2026-07-26")

        self.assertEqual(actual, expected)

    def test_live_soccertv_date_page_parses_league_and_following_channels(self) -> None:
        markdown = """
▴[Europe - UEFA Champions League](https://example.test/competition)
3:00pm[KuPS vs Sabah](https://example.test/match "KuPS vs Sabah")

[Sport One](https://example.test/channels/sport-one/ "Sport One (Sweden)")
4:00pm[Lincoln Red Imps vs Mjällby](https://example.test/match2 "Lincoln vs Mjällby")
"""

        class Response:
            text = markdown

            @staticmethod
            def raise_for_status() -> None:
                return None

        class Session:
            @staticmethod
            def get(*args, **kwargs):
                return Response()

        with patch.object(main, "get_http_session", return_value=Session()):
            matches = main.scrape_live_soccertv("2026-07-28")

        self.assertEqual(len(matches), 2)
        self.assertEqual(matches[0]["date"], "2026-07-28")
        self.assertEqual(matches[0]["league"], "Europe - UEFA Champions League")
        self.assertEqual(matches[0]["channels"], ["Sport One"])
        self.assertEqual(matches[0]["channelCountries"], {"Sport One": ["Sweden"]})

    def test_galatasaray_venezia_channels_are_returned(self) -> None:
        markdown = """
▴[Club Friendly](http://www.livesoccertv.com/competitions/international/club-friendly/)
6:00pm[Galatasaray vs Venezia](http://www.livesoccertv.com/match/galatasaray-vs-venezia/8dqr0 "Galatasaray vs Venezia")

[DAZN Italia](http://www.livesoccertv.com/channels/dazn-italy/ "DAZN Italia"), [Sportdigital FUSSBALL](http://www.livesoccertv.com/channels/sportdigital/ "Sportdigital FUSSBALL"), [S Sport+](http://www.livesoccertv.com/channels/s-sport-plus/ "S Sport+")
"""

        class Response:
            text = markdown

            @staticmethod
            def raise_for_status() -> None:
                return None

        class Session:
            headers = {}

            @staticmethod
            def get(*args, **kwargs):
                return Response()

        with patch.object(main, "get_http_session", return_value=Session()):
            scraped = main.scrape_live_soccertv("2026-07-27")
        with patch.object(main, "get_matches_cached", return_value=scraped):
            response = main.get_tv_channels(
                "Galatasaray", "Venezia", "2026-07-27"
            )

        self.assertEqual(
            response["channels"],
            [
                {"country": "Germany", "channels": ["Sportdigital FUSSBALL"]},
                {"country": "Italy", "channels": ["DAZN Italia"]},
                {"country": "Turkey", "channels": ["S Sport+"]}
            ]
        )

    def test_live_match_with_score_and_minute_keeps_tv_channels(self) -> None:
        markdown = """
▴[Club Friendly](http://www.livesoccertv.com/competitions/international/club-friendly/)
2:30pm 36'[Karlsruher SC 0 - 0 Internazionale](http://www.livesoccertv.com/match/internazionale-vs-karlsruher-sc/2kdt1 "Karlsruher SC vs Internazionale")

[Onefootball](http://www.livesoccertv.com/channels/onefootball-uk/ "Onefootball"), [DAZN Germany](http://www.livesoccertv.com/channels/dazn-europe/ "DAZN Germany"), [DAZN Italia](http://www.livesoccertv.com/channels/dazn-italy/ "DAZN Italia"), [Sport TV2](http://www.livesoccertv.com/channels/sport-tv2/ "Sport TV2"), [Inter TV](http://www.livesoccertv.com/channels/inter-channel/ "Inter TV")
"""

        class Response:
            text = markdown

            @staticmethod
            def raise_for_status() -> None:
                return None

        class Session:
            headers = {}

            @staticmethod
            def get(*args, **kwargs):
                return Response()

        with patch.object(main, "get_http_session", return_value=Session()):
            matches = main.scrape_live_soccertv("2026-07-26")

        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0]["home"], "Karlsruher SC")
        self.assertEqual(matches[0]["away"], "Internazionale")
        self.assertEqual(
            matches[0]["channels"],
            [
                "Onefootball",
                "DAZN Germany",
                "DAZN Italia",
                "Sport TV2",
                "Inter TV"
            ]
        )

    def test_verified_broadcasts_survive_an_empty_scrape(self) -> None:
        with patch.object(main, "get_matches_cached", return_value=[]):
            response = main.get_tv_channels(
                "Karlsruher SC", "Internazionale", "2026-07-26"
            )

        self.assertEqual(
            [group["country"] for group in response["channels"]],
            ["France", "Italy"]
        )
        self.assertEqual(
            response["channels"][0]["channels"],
            ["L'Équipe live foot"]
        )
        self.assertEqual(
            response["channels"][1]["channels"],
            [
                "DAZN Italia",
                "Sky Sport Calcio",
                "NOW TV",
                "Onefootball"
            ]
        )

    def test_tv_response_prioritizes_explicit_european_countries(self) -> None:
        scraped = [match("2026-07-28")]
        scraped[0]["channels"] = [
            "Polsat Sport", "ESPN Select", "Ziggo Sport", "Canal+ Foot", "TNT Sports"
        ]
        scraped[0]["channelCountries"] = {
            "Polsat Sport": ["Poland"],
            "ESPN Select": ["USA"],
            "Ziggo Sport": ["Netherlands"],
            "Canal+ Foot": ["France"],
            "TNT Sports": ["United Kingdom"]
        }

        with patch.object(main, "get_matches_cached", return_value=scraped):
            response = main.get_tv_channels("Home", "Away", "2026-07-28")

        self.assertEqual(
            [group["country"] for group in response["channels"]],
            ["France", "United Kingdom", "Netherlands", "Poland", "USA"]
        )

    def test_requested_european_channel_mappings(self) -> None:
        self.assertEqual(main.get_channel_country("Polsat Sport 1"), "Poland")
        self.assertEqual(main.get_channel_country("Digi Sport 1 Romania"), "Romania")
        self.assertEqual(main.get_channel_country("M4 Sport"), "Hungary")
        self.assertEqual(main.get_channel_country("Cosmote Sport 1 HD"), "Greece")
        self.assertEqual(main.get_channel_country("Sportdigital FUSSBALL"), "Germany")
        self.assertEqual(main.get_channel_country("DAZN Germany"), "Germany")
        self.assertEqual(main.get_channel_country("Inter TV"), "Italy")

    def test_schedule_response_contains_utc_kickoff(self) -> None:
        scraped = [match("2026-07-28")]
        scraped[0]["time"] = "3:00pm"

        with patch.object(main, "get_matches_cached", return_value=scraped):
            response = main.get_schedule("2026-07-28")

        self.assertEqual(response["date"], "2026-07-28")
        self.assertEqual(response["matches"][0]["utcDate"], "2026-07-28T19:00:00Z")


if __name__ == "__main__":
    unittest.main()
