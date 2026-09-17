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
        main.empty_tv_retry_after.clear()
        main.detail_cache.clear()

    def tearDown(self) -> None:
        main.CACHE_DIR = self.previous_cache_dir
        main.cache.clear()
        main.empty_tv_retry_after.clear()
        main.detail_cache.clear()
        self.temp_dir.cleanup()

    def test_concurrent_schedule_requests_share_one_scrape(self) -> None:
        from concurrent.futures import ThreadPoolExecutor
        from threading import Event

        started, release = Event(), Event()
        date = "2026-07-26"

        def scrape(requested):
            started.set()
            self.assertTrue(release.wait(2))
            return [match(requested)]

        with patch.object(main, "scrape_schedule_for_date", side_effect=scrape) as fetch:
            with ThreadPoolExecutor(max_workers=2) as executor:
                first = executor.submit(main.get_matches_cached, date)
                self.assertTrue(started.wait(2))
                second = executor.submit(main.get_matches_cached, date)
                release.set()
                self.assertEqual(first.result(timeout=2), second.result(timeout=2))
        fetch.assert_called_once()

    def test_detail_cache_reuses_channels_and_events(self) -> None:
        fixture = {**match("2026-07-26"), "sourceUrl": "https://www.livesoccertv.com/match/123/"}
        enriched = {**fixture, "events": [{"elapsed": 12, "teamSide": "home"}]}
        with patch.object(main, "enrich_live_soccertv_match", return_value=enriched) as fetch:
            first = main.get_match_detail_cached(fixture)
            second = main.get_match_detail_cached(fixture)
        self.assertEqual(first, second)
        self.assertEqual(second["events"], enriched["events"])
        fetch.assert_called_once()

    def test_expired_detail_failure_preserves_events_and_backs_off(self) -> None:
        fixture = {**match("2026-07-26"), "sourceUrl": "https://www.livesoccertv.com/match/123/"}
        key = (fixture["date"], fixture["sourceUrl"])
        main.detail_cache[key] = {"expiry": 0, "data": {"events": [{"elapsed": 12}]}}
        with patch.object(main, "enrich_live_soccertv_match", side_effect=RuntimeError("offline")) as fetch:
            first = main.get_match_detail_cached(fixture)
            second = main.get_match_detail_cached(fixture)
        self.assertEqual(first["events"], [{"elapsed": 12}])
        self.assertEqual(second, first)
        fetch.assert_called_once()

    def test_stale_schedule_failure_is_not_retried_immediately(self) -> None:
        date = "2026-07-26"
        main.cache[date] = {"expiry": datetime.now() - timedelta(minutes=1), "data": [match(date)]}
        with patch.object(main, "scrape_schedule_for_date", side_effect=RuntimeError("offline")) as fetch:
            self.assertEqual(main.get_matches_cached(date), [match(date)])
            self.assertEqual(main.get_matches_cached(date), [match(date)])
        fetch.assert_called_once()

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

    def test_deduplication_treats_reversed_fixture_as_the_same_match(self) -> None:
        first = match("2026-08-10", "AS Roma", "AS Cannes")
        first["channels"] = ["First"]
        reversed_match = match("2026-08-10", "Cannes", "Roma")
        reversed_match["channels"] = ["Second"]

        result = main.deduplicate_matches([first, reversed_match])

        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["channels"], ["First", "Second"])

    def test_team_matching_keeps_meaningful_club_words(self) -> None:
        self.assertFalse(main.match_teams("Manchester United", "Manchester City"))
        self.assertFalse(main.match_teams("Real Madrid", "Real Sociedad"))
        self.assertFalse(main.match_teams("Athletic Club", "Athletic Ottawa"))

    def test_team_matching_accepts_known_provider_aliases(self) -> None:
        self.assertTrue(main.match_teams("Paris Saint-Germain", "PSG"))
        self.assertTrue(main.match_teams("Olympique de Marseille", "Marseille"))
        self.assertTrue(main.match_teams("Club Sportif Sfaxien", "CS Sfaxien"))
        self.assertTrue(main.match_teams("Internazionale", "Inter Milan"))

    def test_liveonsat_heading_without_year_uses_requested_year(self) -> None:
        self.assertEqual(
            main.parse_liveonsat_date("Sunday, 9th August", current_year=2026),
            "2026-08-09"
        )

    def test_liveonsat_heading_chooses_nearest_year_at_new_year(self) -> None:
        class FixedDatetime(datetime):
            @classmethod
            def now(cls, tz=None):
                return cls(2026, 12, 31, tzinfo=tz)

        with patch.object(main, "datetime", FixedDatetime):
            parsed = main.parse_liveonsat_date("Friday, 1st January")

        self.assertEqual(parsed, "2027-01-01")

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

    def test_schedule_uses_only_live_soccertv(self) -> None:
        expected = [match("2026-08-12")]
        with patch.object(
            main,
            "scrape_live_soccertv",
            return_value=expected
        ) as live_soccer, patch.object(
            main,
            "scrape_live_football_on_tv"
        ) as football_on_tv, patch.object(main, "scrape_liveonsat") as liveonsat:
            actual = main.scrape_schedule_for_date("2026-08-12")

        self.assertEqual(actual, expected)
        live_soccer.assert_called_once_with("2026-08-12")
        football_on_tv.assert_not_called()
        liveonsat.assert_not_called()

    def test_schedule_does_not_fall_through_to_another_source(self) -> None:
        with patch.object(
            main,
            "scrape_live_soccertv",
            side_effect=RuntimeError("LiveSoccerTV unavailable")
        ), patch.object(
            main,
            "scrape_live_football_on_tv",
            return_value=[match("2026-08-12")]
        ) as football_on_tv, patch.object(main, "scrape_liveonsat") as liveonsat:
            with self.assertRaisesRegex(Exception, "LiveSoccerTV unavailable"):
                main.scrape_schedule_for_date("2026-08-12")

        football_on_tv.assert_not_called()
        liveonsat.assert_not_called()

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

    def test_live_soccertv_schedule_exposes_live_half_time_and_scores(self) -> None:
        markdown = """
▴[Club Friendly](https://example.test/competition)
11:30am 47'[Olympique Marseille 2 - 1 Athletic Club](https://example.test/live)
1:00pm HT[Home HT 1 - 1 Away HT](https://example.test/half-time)
9:00am FT[Home FT 3 - 0 Away FT](https://example.test/finished)
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
            matches = main.scrape_live_soccertv("2026-08-09")

        self.assertEqual(matches[0]["status"], "LIVE")
        self.assertEqual(matches[0]["minute"], 47)
        self.assertEqual(matches[0]["homeScore"], 2)
        self.assertEqual(matches[0]["awayScore"], 1)
        self.assertEqual(matches[1]["status"], "HALF_TIME")
        self.assertEqual(matches[1]["statusLabel"], "Mi-temps")
        self.assertEqual(matches[2]["status"], "FINISHED")

    def test_live_soccertv_requests_only_the_daily_schedule_table(self) -> None:
        future_date = (datetime.now().date() + timedelta(days=1)).isoformat()
        markdown = """
▴[League](https://example.test/competition)
3:00pm[Home vs Away](https://example.test/match "Home vs Away")
"""
        captured = {}

        class Response:
            status_code = 200
            text = markdown

            @staticmethod
            def raise_for_status() -> None:
                return None

        class Session:
            headers = {}

            @staticmethod
            def get(url, **kwargs):
                captured["url"] = url
                captured.update(kwargs)
                return Response()

        with patch.object(main, "get_http_session", return_value=Session()):
            matches = main.scrape_live_soccertv(future_date)

        self.assertEqual(len(matches), 1)
        self.assertTrue(captured["url"].endswith(f"/{future_date}/"))
        self.assertEqual(
            captured["headers"]["X-Target-Selector"],
            "table.schedules"
        )
        self.assertEqual(captured["headers"]["X-Respond-With"], "markdown")
        self.assertEqual(
            captured["headers"]["X-Cache-Tolerance"],
            str(main.JINA_FUTURE_CACHE_TOLERANCE_SECONDS)
        )
        self.assertEqual(captured["timeout"], 15)

    def test_full_page_fallback_does_not_assign_target_date_without_heading(self) -> None:
        markdown = """
▴[Club Friendly](https://example.test/competition)
3:00pm[Home vs Away](https://example.test/match "Home vs Away")
"""

        class Response:
            def __init__(self, status_code: int) -> None:
                self.status_code = status_code
                self.text = markdown

            @staticmethod
            def raise_for_status() -> None:
                return None

        class Session:
            headers = {}
            calls = 0

            @classmethod
            def get(cls, *args, **kwargs):
                cls.calls += 1
                return Response(422 if cls.calls == 1 else 200)

        with patch.object(main, "get_http_session", return_value=Session()):
            with self.assertRaisesRegex(
                Exception, "Failed to parse LiveSoccerTV schedule content"
            ):
                main.scrape_live_soccertv("2026-08-10")

    def test_full_page_fallback_uses_explicit_date_heading(self) -> None:
        markdown = """
# Monday, 10th August
▴[Club Friendly](https://example.test/competition)
3:00pm[Home vs Away](https://example.test/match "Home vs Away")
"""

        class Response:
            def __init__(self, status_code: int) -> None:
                self.status_code = status_code
                self.text = markdown

            @staticmethod
            def raise_for_status() -> None:
                return None

        class Session:
            headers = {}
            calls = 0

            @classmethod
            def get(cls, *args, **kwargs):
                cls.calls += 1
                return Response(422 if cls.calls == 1 else 200)

        with patch.object(main, "get_http_session", return_value=Session()):
            matches = main.scrape_live_soccertv("2026-08-10")

        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0]["date"], "2026-08-10")

    def test_implausibly_large_daily_schedule_is_rejected(self) -> None:
        markdown = "\n".join(
            f"3:00pm[Home {index} vs Away {index}](https://example.test/{index})"
            for index in range(main.MAX_DAILY_SCHEDULE_MATCHES + 1)
        )

        class Response:
            status_code = 200
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
            with self.assertRaisesRegex(Exception, "Rejected implausible"):
                main.scrape_live_soccertv("2026-08-10")

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
        with patch.object(
            main, "get_matches_cached", return_value=scraped
        ), patch.object(
            main, "enrich_live_soccertv_match", side_effect=lambda fixture: fixture
        ):
            response = main.get_tv_channels(
                "Galatasaray", "Venezia", "2026-07-27"
            )

        self.assertEqual(
            response["channels"],
            [
                {"country": "Italy", "channels": ["DAZN Italia"]},
                {"country": "Germany", "channels": ["Sportdigital FUSSBALL"]},
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

    def test_half_time_match_is_parsed_with_its_detail_url(self) -> None:
        markdown = """
▴[Club Friendly](http://www.livesoccertv.com/competitions/international/club-friendly/)
11:30am HT[Manchester City 1 - 1 Internazionale](http://www.livesoccertv.com/match/manchester-city-vs-internazionale/1juxp#5634286 "Manchester City vs Internazionale")

[CITY+](http://www.livesoccertv.com/channels/man-city-for-tv/ "CITY+"), [Onefootball](http://www.livesoccertv.com/channels/onefootball-uk/ "Onefootball")
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
            matches = main.scrape_live_soccertv("2026-08-01")

        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0]["home"], "Manchester City")
        self.assertEqual(matches[0]["away"], "Internazionale")
        self.assertEqual(matches[0]["channels"], ["CITY+", "Onefootball"])
        self.assertEqual(
            matches[0]["sourceUrl"],
            "http://www.livesoccertv.com/match/manchester-city-vs-internazionale/1juxp"
        )

    def test_unrelated_channel_link_does_not_leak_into_previous_match(self) -> None:
        markdown = """
▴[Club Friendly](http://www.livesoccertv.com/competition)
1:00pm[Home vs Away](http://www.livesoccertv.com/match/home-vs-away/abc "Home vs Away")
Navigation text
[Unrelated](http://www.livesoccertv.com/channels/unrelated/ "Unrelated")
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
            matches = main.scrape_live_soccertv("2026-08-01")

        self.assertEqual(matches[0]["channels"], [])

    def test_international_coverage_is_grouped_by_explicit_country(self) -> None:
        markdown = """
## International Coverage

#### Manchester City vs Internazionale Live Stream and TV Schedule

Italy[DAZN Italia](https://www.livesoccertv.com/channels/dazn-italy/)[Sky Sport Calcio](https://www.livesoccertv.com/channels/sky-sport-serie-a/)
San Marino[Sky Sport Calcio](https://www.livesoccertv.com/channels/sky-sport-serie-a/)
## Match Details
"""

        channels, countries = main.parse_live_soccertv_international_coverage(
            markdown
        )

        self.assertEqual(channels, ["DAZN Italia", "Sky Sport Calcio"])
        self.assertEqual(countries["DAZN Italia"], ["Italy"])
        self.assertEqual(countries["Sky Sport Calcio"], ["Italy", "San Marino"])

    def test_live_soccertv_events_parse_goals_substitutions_and_cards(self) -> None:
        markdown = """
## Events
#### Arsenal vs Real Betis Match Events
9'[R. Riquelme](https://www.livesoccertv.com/players/riquelme/) (0 - 1)
Assist: [P. Fornals](https://www.livesoccertv.com/players/fornals/)
[P. Hincapie](https://www.livesoccertv.com/players/hincapie/) (1 - 1)
Assist: [C. Tzolis](https://www.livesoccertv.com/players/tzolis/)32'
[M. Salmon](https://www.livesoccertv.com/players/salmon/) / [C. Mosquera](https://www.livesoccertv.com/players/mosquera/)46'
46'[Isco](https://www.livesoccertv.com/players/isco/) / [G. Bouare](https://www.livesoccertv.com/players/bouare/)
[M. Salmon](https://www.livesoccertv.com/players/salmon/)61'
63'O. Konate / [M. Abline](https://www.livesoccertv.com/players/abline/)
88'[P. Brunner (pen.)](https://www.livesoccertv.com/players/brunner/) (2 - 2)
Assist: A. Soubeir
90+1'O. Konate
## Lineups
"""

        events = main.parse_live_soccertv_events(markdown)
        snapshot = main.extract_live_soccertv_events_snapshot(markdown)

        self.assertIn("R. Riquelme", snapshot)
        self.assertIn("Assist: A. Soubeir", snapshot)
        self.assertIn("90+1'O. Konate", snapshot)
        self.assertNotIn("https://", snapshot)
        self.assertEqual(events[0], {
            "elapsed": 9,
            "extra": None,
            "teamSide": "away",
            "type": "Goal",
            "detail": "Goal",
            "player": "R. Riquelme",
            "assist": "P. Fornals"
        })
        self.assertEqual(events[1]["elapsed"], 32)
        self.assertEqual(events[1]["teamSide"], "home")
        self.assertEqual(events[2]["teamSide"], "home")
        self.assertEqual(events[2]["player"], "C. Mosquera")
        self.assertEqual(events[2]["assist"], "M. Salmon")
        self.assertEqual(events[3]["teamSide"], "away")
        self.assertEqual(events[4]["type"], "Card")
        self.assertEqual(events[4]["elapsed"], 61)
        self.assertEqual(events[5]["type"], "subst")
        self.assertEqual(events[5]["assist"], "O. Konate")
        self.assertEqual(events[5]["player"], "M. Abline")
        self.assertEqual(events[6]["detail"], "Penalty")
        self.assertEqual(events[6]["player"], "P. Brunner")
        self.assertEqual(events[6]["assist"], "A. Soubeir")
        self.assertEqual(events[7]["elapsed"], 90)
        self.assertEqual(events[7]["extra"], 1)
        self.assertEqual(events[7]["player"], "O. Konate")

    def test_tv_response_exposes_confirmed_source_metadata(self) -> None:
        scraped = match(
            "2026-08-01", "Manchester City", "Internazionale"
        )
        scraped.update({
            "source": "LiveSoccerTV",
            "sourceUrl": "https://www.livesoccertv.com/match/example"
        })
        enriched = {
            **scraped,
            "channels": ["Sky Sport Calcio"],
            "channelCountries": {"Sky Sport Calcio": ["Italy"]},
            "events": [{
                "elapsed": 9,
                "extra": None,
                "teamSide": "away",
                "type": "Goal",
                "detail": "Goal",
                "player": "R. Riquelme",
                "assist": "P. Fornals"
            }],
            "eventsSnapshot": "9' R. Riquelme (0 - 1)\nAssist: P. Fornals",
            "verifiedAt": "2026-08-01T12:35:00+00:00"
        }

        with patch.object(
            main, "get_matches_cached", return_value=[scraped]
        ), patch.object(
            main, "enrich_live_soccertv_match", return_value=enriched
        ):
            response = main.get_tv_channels(
                "Manchester City", "Internazionale", "2026-08-01"
            )

        self.assertEqual(response["status"], "confirmed")
        self.assertEqual(response["source"], "LiveSoccerTV")
        self.assertEqual(response["events"], enriched["events"])
        self.assertEqual(response["eventsSnapshot"], enriched["eventsSnapshot"])
        self.assertEqual(
            response["channels"],
            [{"country": "Italy", "channels": ["Sky Sport Calcio"]}]
        )

    def test_tv_match_accepts_reversed_home_and_away(self) -> None:
        reversed_match = match(
            "2026-07-26", home="Roma", away="Cannes"
        )
        reversed_match["channels"] = ["DAZN Italia"]
        reversed_match["events"] = [{
            "elapsed": 12,
            "extra": None,
            "teamSide": "home",
            "type": "Goal",
            "detail": "Goal",
            "player": "Player",
            "assist": None
        }]

        with patch.object(
            main, "get_matches_cached", return_value=[reversed_match]
        ):
            response = main.get_tv_channels(
                "AS Cannes", "AS Roma", "2026-07-26"
            )

        self.assertEqual(
            [group["country"] for group in response["channels"]],
            ["Italy"]
        )
        self.assertEqual(
            response["channels"][0]["channels"],
            ["DAZN Italia"]
        )
        self.assertEqual(response["events"][0]["teamSide"], "away")

    def test_empty_tv_result_forces_a_fresh_scrape(self) -> None:
        cached = match("2026-07-26", "Roma", "Cannes")
        cached["channels"] = []
        fresh = {**cached, "channels": ["DAZN Italia"]}

        with patch.object(
            main, "get_matches_cached", return_value=[cached]
        ), patch.object(
            main, "scrape_match_for_date", return_value=[fresh]
        ) as scraper:
            response = main.get_tv_channels(
                "AS Cannes", "AS Roma", "2026-07-26"
            )

        scraper.assert_called_once_with("AS Cannes", "AS Roma", "2026-07-26")
        self.assertEqual(
            response["channels"],
            [{"country": "Italy", "channels": ["DAZN Italia"]}]
        )

    def test_known_match_page_does_not_reload_daily_schedule(self) -> None:
        cached = match("2026-07-26", "Roma", "Cannes")
        cached.update({
            "channels": [],
            "sourceUrl": "https://www.livesoccertv.com/match/roma-vs-cannes/123"
        })
        detailed = {
            **cached,
            "channels": ["DAZN Italia"],
            "channelCountries": {"DAZN Italia": ["Italy"]}
        }

        with patch.object(
            main, "get_matches_cached", return_value=[cached]
        ), patch.object(
            main, "enrich_live_soccertv_match", return_value=detailed
        ) as detail, patch.object(
            main, "scrape_match_for_date"
        ) as schedule:
            response = main.get_tv_channels(
                "Roma", "Cannes", "2026-07-26"
            )

        detail.assert_called_once_with(cached)
        schedule.assert_not_called()
        self.assertEqual(
            response["channels"],
            [{"country": "Italy", "channels": ["DAZN Italia"]}]
        )

    def test_psg_alias_matches_reversed_live_soccertv_fixture(self) -> None:
        scraped = match(
            "2026-08-08", home="Manchester United", away="PSG"
        )
        scraped.update({
            "source": "LiveSoccerTV",
            "sourceUrl": "https://www.livesoccertv.com/match/manchester-united-vs-psg/1bea6"
        })
        enriched = {
            **scraped,
            "channels": ["STC TV", "beIN Sports 1", "MUTV"],
            "channelCountries": {
                "STC TV": ["Algeria", "Qatar", "Tunisia"],
                "beIN Sports 1": ["France"],
                "MUTV": ["Great Britain", "Ireland Republic"]
            }
        }

        with patch.object(
            main, "get_matches_cached", return_value=[scraped]
        ), patch.object(
            main, "enrich_live_soccertv_match", return_value=enriched
        ):
            response = main.get_tv_channels(
                "Paris Saint-Germain", "Manchester United", "2026-08-08"
            )

        self.assertEqual(response["status"], "confirmed")
        self.assertEqual(response["channels"], [
            {"country": "MENA (Middle East)", "channels": ["STC TV"]},
            {"country": "France", "channels": ["beIN Sports 1"]},
            {"country": "United Kingdom", "channels": ["MUTV"]},
            {"country": "Ireland", "channels": ["MUTV"]}
        ])

    def test_tv_match_uses_stable_source_url_before_team_names(self) -> None:
        expected = match(
            "2026-08-08", home="Bayer Leverkusen", away="Sevilla"
        )
        expected.update({
            "source": "LiveSoccerTV",
            "sourceUrl": "https://www.livesoccertv.com/match/bayer-leverkusen-vs-sevilla/abc"
        })
        unrelated = match(
            "2026-08-08", home="Bayern München", away="Sevilla"
        )
        unrelated["sourceUrl"] = (
            "https://www.livesoccertv.com/match/bayern-munchen-vs-sevilla/xyz"
        )
        enriched = {
            **expected,
            "channels": ["Premier Sports 1"],
            "channelCountries": {"Premier Sports 1": ["Great Britain"]}
        }

        with patch.object(
            main, "get_matches_cached", return_value=[unrelated, expected]
        ), patch.object(
            main, "enrich_live_soccertv_match", return_value=enriched
        ):
            response = main.get_tv_channels(
                "Bayern Leverkusen",
                "Seville",
                "2026-08-08",
                source_url=(
                    "http://livesoccertv.com/match/"
                    "bayer-leverkusen-vs-sevilla/abc#123"
                )
            )

        self.assertEqual(response["match"], "Bayer Leverkusen vs Sevilla")
        self.assertEqual(response["channels"], [{
            "country": "United Kingdom",
            "channels": ["Premier Sports 1"]
        }])

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
            ["France", "United Kingdom", "Poland", "Netherlands", "USA"]
        )

    def test_tv_response_keeps_only_selected_regions_and_requested_priority(self) -> None:
        scraped = [match("2026-07-28")]
        requested = [
            "France", "Italy", "Spain", "England", "Germany", "Poland",
            "Romania", "Qatar", "United States", "Canada", "Netherlands",
            "Japan"
        ]
        scraped[0]["channels"] = [f"Channel {country}" for country in requested]
        scraped[0]["channelCountries"] = {
            f"Channel {country}": [country] for country in requested
        }

        with patch.object(main, "get_matches_cached", return_value=scraped):
            response = main.get_tv_channels("Home", "Away", "2026-07-28")

        self.assertEqual(
            [group["country"] for group in response["channels"]],
            [
                "France", "Italy", "Spain", "United Kingdom", "Germany",
                "Poland", "Romania", "Netherlands", "Qatar", "USA",
                "Canada"
            ]
        )
        self.assertEqual(len(response["channels"]), len(requested) - 1)

    def test_tv_response_excludes_unwanted_world_regions(self) -> None:
        scraped = [match("2026-07-28")]
        scraped[0]["channels"] = [
            "France TV", "Tunisia TV", "USA TV", "Japan TV", "Brazil TV",
            "Australia TV", "South Africa TV"
        ]
        scraped[0]["channelCountries"] = {
            "France TV": ["France"],
            "Tunisia TV": ["Tunisia"],
            "USA TV": ["USA"],
            "Japan TV": ["Japan"],
            "Brazil TV": ["Brazil"],
            "Australia TV": ["Australia"],
            "South Africa TV": ["South Africa"]
        }

        with patch.object(main, "get_matches_cached", return_value=scraped):
            response = main.get_tv_channels("Home", "Away", "2026-07-28")

        self.assertEqual(
            [group["country"] for group in response["channels"]],
            ["France", "Tunisia", "USA"]
        )

    def test_bein_mena_countries_are_collapsed_into_one_group(self) -> None:
        scraped = [match("2026-07-28")]
        scraped[0]["channels"] = ["beIN SPORTS 1", "beIN SPORTS France"]
        scraped[0]["channelCountries"] = {
            "beIN SPORTS 1": ["Algeria", "Qatar", "Tunisia"],
            "beIN SPORTS France": ["France"]
        }

        with patch.object(main, "get_matches_cached", return_value=scraped):
            response = main.get_tv_channels("Home", "Away", "2026-07-28")

        self.assertEqual(response["channels"], [
            {"country": "MENA (Middle East)", "channels": ["beIN SPORTS 1"]},
            {"country": "France", "channels": ["beIN SPORTS France"]}
        ])

    def test_country_aliases_are_merged(self) -> None:
        scraped = [match("2026-07-28")]
        scraped[0]["channels"] = ["English One", "English Two"]
        scraped[0]["channelCountries"] = {
            "English One": ["England"],
            "English Two": ["United Kingdom"]
        }

        with patch.object(main, "get_matches_cached", return_value=scraped):
            response = main.get_tv_channels("Home", "Away", "2026-07-28")

        self.assertEqual(response["channels"], [{
            "country": "United Kingdom",
            "channels": ["English One", "English Two"]
        }])

    def test_requested_european_channel_mappings(self) -> None:
        self.assertEqual(main.get_channel_country("Polsat Sport 1"), "Poland")
        self.assertEqual(main.get_channel_country("Digi Sport 1 Romania"), "Romania")
        self.assertEqual(main.get_channel_country("M4 Sport"), "Hungary")
        self.assertEqual(main.get_channel_country("Cosmote Sport 1 HD"), "Greece")
        self.assertEqual(main.get_channel_country("Sportdigital FUSSBALL"), "Germany")
        self.assertEqual(main.get_channel_country("DAZN Germany"), "Germany")
        self.assertEqual(main.get_channel_country("Inter TV"), "Italy")
        self.assertEqual(main.get_channel_country("Alkass Sports"), "Qatar")
        self.assertEqual(main.get_channel_country("TSN 1"), "Canada")
        self.assertEqual(main.get_channel_country("beIN Sports Premium 1"), "MENA (Middle East)")
        self.assertEqual(main.get_channel_country("Canal 5 Televisa"), "Mexico")
        self.assertEqual(main.get_channel_country("MUTV"), "United Kingdom & Ireland")
        self.assertEqual(main.get_channel_country("myCANAL"), "France")
        self.assertEqual(main.get_channel_country("STC TV"), "MENA (Middle East)")
        self.assertEqual(main.get_channel_country("VG+"), "Norway")
        self.assertEqual(main.get_channel_country("Sport Bladet Play"), "Sweden")

    def test_schedule_response_contains_utc_kickoff(self) -> None:
        scraped = [match("2026-07-28")]
        scraped[0]["time"] = "3:00pm"
        scraped[0]["source"] = "LiveSoccerTV"

        with patch.object(main, "get_matches_cached", return_value=scraped):
            response = main.get_schedule("2026-07-28")

        self.assertEqual(response["date"], "2026-07-28")
        self.assertEqual(response["matches"][0]["utcDate"], "2026-07-28T15:00:00Z")


if __name__ == "__main__":
    unittest.main()
