from fastapi import FastAPI, Query, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import json
import logging
import os
import requests
from bs4 import BeautifulSoup
import re
import unicodedata
from datetime import datetime, timedelta
from pathlib import Path
from requests.adapters import HTTPAdapter
from typing import Callable, List, Dict, Optional
from urllib3.util.retry import Retry
from zoneinfo import ZoneInfo

app = FastAPI(
    title="Football TV Channels Scraper API",
    description="Microservice to scrape liveonsat.com and return TV channel broadcasts grouped by region.",
    version="1.0.0"
)

# Enable CORS for local testing and web integration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Cache mémoire + un fichier JSON par date. Les fichiers expirés restent disponibles
# comme secours si les sites de scraping sont momentanément injoignables.
cache: Dict[str, Dict] = {}
empty_tv_retry_after: Dict[str, datetime] = {}
DEFAULT_CACHE_DIR = Path(__file__).resolve().parent / "tv_cache"
CACHE_DIR = Path(os.environ.get("TV_CACHE_DIR", str(DEFAULT_CACHE_DIR)))
CACHE_DURATION = timedelta(minutes=15)
EMPTY_CACHE_DURATION = timedelta(minutes=2)
LOGGER = logging.getLogger("football-tv-cache")

EUROPE_BROADCAST_REGIONS = {
    "Albania", "Andorra", "Austria", "Belgium", "Bosnia and Herzegovina",
    "Bulgaria", "Croatia", "Cyprus", "Czech Republic", "Czechia", "Denmark",
    "Estonia", "Finland", "France", "Germany", "Greece", "Hungary", "Iceland",
    "Ireland", "Italy", "Kosovo", "Latvia", "Liechtenstein", "Lithuania",
    "Luxembourg", "Malta", "Moldova", "Monaco", "Montenegro", "Netherlands",
    "North Macedonia", "Norway", "Poland", "Portugal", "Romania", "San Marino",
    "Serbia", "Slovakia", "Slovenia", "Spain", "Sweden", "Switzerland",
    "Turkey", "Türkiye", "Ukraine", "United Kingdom", "Vatican City",
    "United Kingdom & Ireland", "Balkans", "Scandinavia & Baltics"
}

PRIORITY_N1_BROADCAST_REGIONS = [
    "France",
    "United Kingdom",
    "United Kingdom & Ireland",
    "Germany",
    "Spain",
    "Italy",
    "Portugal",
    "Switzerland",
    "Austria",
    "Belgium",
    "Netherlands"
]
PRIORITY_N1_RANK = {
    country: rank
    for rank, country in enumerate(PRIORITY_N1_BROADCAST_REGIONS)
}

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Accept-Encoding": "gzip, deflate",
    "Connection": "keep-alive",
    "Referer": "https://liveonsat.com/",
    "Upgrade-Insecure-Requests": "1",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-User": "?1",
    "Sec-Fetch-Dest": "document"
}

USER_AGENTS = [
    HEADERS["User-Agent"],
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Firefox/123.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
]

LIVEONSAT_URLS = [
    "https://liveonsat.com/2day.php",
    "https://www.liveonsat.com/2day.php"
]
LIVEFOOTBALLONTV_URL = "https://www.live-footballontv.com/"
LIVE_SOCCERTV_PROXY = "https://r.jina.ai/http://www.livesoccertv.com/schedules"
RETRY_STRATEGY = Retry(
    total=3,
    status_forcelist=[429, 500, 502, 503, 504],
    allowed_methods=["GET", "HEAD", "OPTIONS"],
    backoff_factor=0.5
)

def cache_file_for(date: str) -> Path:
    return CACHE_DIR / f"{date}.json"


def load_cache_from_disk(date: str) -> Optional[Dict]:
    cache_file = cache_file_for(date)
    if not cache_file.exists():
        return None
    try:
        raw = json.loads(cache_file.read_text(encoding="utf-8"))
        expiry = raw.get("expiry")
        entry = {
            "expiry": datetime.fromisoformat(expiry) if expiry else None,
            "data": raw.get("data") or []
        }
        cache[date] = entry
        return entry
    except (OSError, ValueError, TypeError) as exc:
        LOGGER.warning("Unable to read cache for %s: %s", date, exc)
        return None


def save_cache_to_disk(date: str, matches: List[Dict]) -> None:
    expiry = datetime.now() + (
        CACHE_DURATION if matches else EMPTY_CACHE_DURATION
    )
    entry = {"expiry": expiry, "data": matches}
    cache[date] = entry
    try:
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        target = cache_file_for(date)
        temporary = target.with_suffix(".json.tmp")
        temporary.write_text(json.dumps({
            "date": date,
            "expiry": expiry.isoformat(),
            "data": matches
        }, ensure_ascii=False), encoding="utf-8")
        temporary.replace(target)
    except OSError as exc:
        LOGGER.warning("Unable to write cache for %s: %s", date, exc)


def save_scraped_dates(matches: List[Dict]) -> None:
    matches_by_date: Dict[str, List[Dict]] = {}
    for match in matches:
        date = match.get("date")
        if date and re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
            matches_by_date.setdefault(date, []).append(match)
    for date, date_matches in matches_by_date.items():
        save_cache_to_disk(date, deduplicate_matches(date_matches))


def deduplicate_matches(matches: List[Dict]) -> List[Dict]:
    by_key: Dict[tuple, Dict] = {}
    for match in matches:
        key = (
            match.get("date"),
            " ".join(sorted(normalize_team_name(match.get("home", "")))),
            " ".join(sorted(normalize_team_name(match.get("away", ""))))
        )
        existing = by_key.get(key)
        if existing is None:
            by_key[key] = match
            continue
        channels = list(dict.fromkeys(
            (existing.get("channels") or []) + (match.get("channels") or [])
        ))
        channel_countries = {
            channel: list(countries)
            for channel, countries in (existing.get("channelCountries") or {}).items()
        }
        for channel, countries in (match.get("channelCountries") or {}).items():
            channel_countries[channel] = list(dict.fromkeys(
                channel_countries.get(channel, []) + countries
            ))
        by_key[key] = {
            **existing,
            "channels": channels,
            "channelCountries": channel_countries
        }
    return list(by_key.values())


def get_http_session() -> requests.Session:
    session = requests.Session()
    session.trust_env = False
    session.mount("https://", HTTPAdapter(max_retries=RETRY_STRATEGY))
    session.headers.update(HEADERS)
    return session


def parse_liveonsat_date(date_str: str, current_year: int = None) -> Optional[str]:
    """Parse date strings like 'Sunday, 24th  May' into YYYY-MM-DD format."""
    if not current_year:
        current_year = datetime.now().year
        
    date_str = date_str.strip()
    if not date_str or date_str.lower() == "football":
        return None
        
    if "," in date_str:
        date_str = date_str.split(",")[1].strip()
        
    # Remove ordinal suffixes: 24th -> 24
    date_str = re.sub(r'(\d+)(st|nd|rd|th)', r'\1', date_str)
    
    # Normalize spaces
    date_str = " ".join(date_str.split())
    
    has_year = re.search(r'\b\d{4}\b', date_str) is not None
    if has_year:
        formats = (
            "%A %d %B %Y", "%A %d %b %Y",
            "%d %B %Y", "%d %b %Y",
            "%B %d %Y", "%b %d %Y",
            "%A %B %d %Y", "%A %b %d %Y"
        )
    else:
        formats = (
            "%A %d %B", "%A %d %b",
            "%d %B", "%d %b",
            "%B %d %Y", "%b %d %Y",
            "%A %B %d %Y", "%A %b %d %Y"
        )

    for fmt in formats:
        try:
            if has_year or fmt.endswith("%Y"):
                dt = datetime.strptime(f"{date_str} {current_year}" if not has_year else date_str, fmt)
            else:
                dt = datetime.strptime(date_str, fmt)
            return dt.strftime("%Y-%m-%d")
        except ValueError:
            continue
    return None


CHANNEL_MARKDOWN_RE = re.compile(
    r'\[([^\]]+)\]\((https?://[^\s)]*/channels/[^\s)]*)'
    r'(?:\s+"([^"]*)")?\)'
)


def countries_from_channel_title(title: Optional[str]) -> List[str]:
    """Extract territory metadata such as '(Italy, San Marino)' from a link title."""
    if not title:
        return []
    groups = re.findall(r'\(([^()]*)\)', title)
    if not groups:
        return []
    territory_text = groups[-1].strip()
    low = territory_text.lower()
    if "live stream" in low or "on demand" in low:
        return []
    return [
        country.strip().rstrip("…").strip()
        for country in territory_text.split(",")
        if country.strip()
    ]


def extract_markdown_channels(line: str) -> List[tuple]:
    return [
        (name.strip(), countries_from_channel_title(title))
        for name, _url, title in CHANNEL_MARKDOWN_RE.findall(line)
        if name.strip()
    ]


def scrape_live_soccertv(target_date: Optional[str] = None) -> List[Dict]:
    """Scrapes LiveSoccerTV schedule content via a markdown proxy."""
    session = get_http_session()
    # Jina rejects the browser-navigation headers required by the HTML scrapers.
    # A minimal requests session is accepted and returns plain Markdown.
    if hasattr(session, "headers"):
        session.headers.clear()
    schedule_url = (
        f"{LIVE_SOCCERTV_PROXY}/{target_date}/"
        if target_date
        else f"{LIVE_SOCCERTV_PROXY}/"
    )
    response = session.get(schedule_url, timeout=30)
    response.raise_for_status()
    text = response.text

    matches = []
    # The date-specific Jina page does not contain a Markdown date heading.
    # In that case the requested date is the authoritative schedule date.
    current_date = target_date
    current_league = "Unknown"
    last_match = None
    target_year = int(target_date[:4]) if target_date else None

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        line = re.sub(r'^[\*\-\+]\s*', '', line)

        league_line = re.match(r'^▴\[([^\]]+)\]\(', line)
        if league_line:
            current_league = league_line.group(1).strip()
            last_match = None
            continue

        if line.startswith('#'):
            current_date = parse_liveonsat_date(
                line.lstrip('#').strip(),
                current_year=target_year
            )
            continue

        match_line = re.match(
            r'^(?:Live\s+)?(?P<time>\d{1,2}:\d{2}\s*(?:am|pm))'
            r"(?:\s+\d{1,3}(?:\+\d+)?')?\s*"
            r'\[(?P<teams>[^\]]+)\]\([^\)]*\)(?P<rest>.*)$',
            line,
            flags=re.I
        )
        if not match_line:
            if last_match is not None:
                for channel, countries in extract_markdown_channels(line):
                    if channel not in last_match["channels"]:
                        last_match["channels"].append(channel)
                    if countries:
                        previous = last_match["channelCountries"].get(channel, [])
                        last_match["channelCountries"][channel] = list(dict.fromkeys(
                            previous + countries
                        ))
            continue
        if not current_date:
            continue

        teams_text = match_line.group('teams').strip()
        teams = re.split(r'\s+v[s]?\s+', teams_text, flags=re.I)
        if len(teams) < 2:
            live_score_teams = re.match(
                r'^(.*?)\s+\d+\s*-\s*\d+\s+(.*?)$',
                teams_text
            )
            if live_score_teams:
                teams = [
                    live_score_teams.group(1),
                    live_score_teams.group(2)
                ]
        if len(teams) < 2:
            continue

        home_team = teams[0].strip()
        away_team = teams[1].strip()
        kickoff_time = match_line.group('time').strip()

        channel_links = extract_markdown_channels(match_line.group('rest'))
        channels = [channel for channel, _countries in channel_links]
        channel_countries = {
            channel: countries
            for channel, countries in channel_links
            if countries
        }
        last_match = {
            "date": current_date,
            "league": current_league,
            "home": home_team,
            "away": away_team,
            "time": kickoff_time,
            "channels": channels,
            "channelCountries": channel_countries
        }
        matches.append(last_match)

    if not matches:
        raise Exception("Failed to parse LiveSoccerTV schedule content.")
    return matches


def scrape_live_football_on_tv() -> List[Dict]:
    """Scrapes live-footballontv.com and parses fixtures with TV channel info."""
    session = get_http_session()
    response = session.get(LIVEFOOTBALLONTV_URL, timeout=20, allow_redirects=True)
    response.raise_for_status()

    soup = BeautifulSoup(response.text, "html.parser")
    matches = []

    for group in soup.select("div.fixture-group"):
        date_div = group.find("div", class_="fixture-date")
        if not date_div:
            continue
        date = parse_liveonsat_date(date_div.get_text(" ", strip=True))
        if not date:
            continue

        for fixture in group.select("div.fixture"):
            teams_div = fixture.find("div", class_="fixture__teams")
            if not teams_div:
                continue

            teams_text = " ".join(teams_div.get_text(" ").split())
            teams = re.split(r'\s+v[s]?\s+', teams_text)
            if len(teams) < 2:
                continue

            home_team = teams[0].strip()
            away_team = teams[1].strip()
            time_div = fixture.find("div", class_="fixture__time")
            kickoff_time = time_div.get_text(strip=True) if time_div else "Unknown"
            league_div = fixture.find("div", class_="fixture__competition")
            league = league_div.get_text(strip=True) if league_div else "Unknown"

            channels = []
            for pill in fixture.select("span.channel-pill"):
                chan_name = pill.get_text(" ", strip=True)
                if chan_name:
                    channels.append(chan_name)

            matches.append({
                "date": date,
                "league": league,
                "home": home_team,
                "away": away_team,
                "time": kickoff_time,
                "channels": channels
            })

    if not matches:
        raise Exception("Failed to parse Live Football On TV fixtures.")
    return matches


def get_channel_country(channel_name: str) -> str:
    """Map TV channel names to their corresponding countries or regions."""
    channel_name_lower = channel_name.lower()
    
    mappings = [
        ("United Kingdom & Ireland", ["uk", "great britain", "gb", "sky sports", "premier sports gb", "tnt sports", "itv", "bbc", "discovery+"]),
        ("Ireland", ["ireland", "eire"]),
        ("France", ["france", "canal+ live", "canal+ foot", "rmc sport", "bein sports france"]),
        ("Spain", ["espana", "españa", "laliga", "movistar", "dazn españa", "dazn espana"]),
        ("Italy", [
            "italia", "calcio", "sky sport 25", "sky sport italia", "rai",
            "inter tv"
        ]),
        ("Germany", [
            "deutsch", "bundesliga", "sky sport premier league de",
            "dazn 1 bar deutsch", "dazn de", "dazn germany", "sportdigital"
        ]),
        ("Portugal", ["portugal", "sport tv"]),
        ("Netherlands", ["nederland", "ziggo", "espn 1 nederland"]),
        ("Belgium", ["belgium", "play sports"]),
        ("Switzerland", ["switzerland", "rsi la", "srf ", "blue sport", "teleclub", "rts deux"]),
        ("Austria", ["austria", "orf eins", "orf 1", "orf on"]),
        ("Croatia", ["croatia", "hrvatska", "maxtv", "hrt 2"]),
        ("Slovenia", ["slovenia", "kanal a", "sportklub slovenia"]),
        ("Czechia", ["czech", "čt sport", "ct sport", "nova sport"]),
        ("Slovakia", ["slovakia", "joj sport"]),
        ("Balkans", ["bih", "srbija", "hrvatska", "slovenija", "montenegro", "arena premium", "arena sport", "sportklub", "art motion"]),
        ("Scandinavia & Baltics", ["norge", "sverige", "suomi", "danmark", "dansk", "svensk", "baltic", "baltics", "v sport", "viaplay", "voyo", "tv2 play"]),
        ("Turkey", ["türkiye", "turkey", "tivibu", "exxen", "s sport"]),
        ("Bulgaria", ["bulgaria", "diema", "max sport"]),
        ("Romania", ["romania", "digi sport", "prima sport", "primaplay", "voyo pro tv"]),
        ("Poland", ["poland", "polska", "polsat sport", "tvp sport", "canal+ polska"]),
        ("Ukraine", ["ukraine", "megogo", "setanta sports ukraine"]),
        ("Hungary", ["hungary", "magyar", "spíler", "match 4", "m4 sport"]),
        ("Greece", ["hellas", "greece", "greek", "cosmote", "nova sports"]),
        ("USA", ["usa", "nbc", "cnbc", "paramount+", "peacock", "universo", "telemundo", "syfy"]),
        ("Canada", ["canada", "fubo tv canada", "fubotv canada"]),
        ("Australia", ["australia", "stan sport", "optus"]),
        ("MENA (Middle East)", ["mena", "bein sports mena", "ssc", "dubai sports", "abu dhabi"]),
        ("Africa", ["africa", "supersport", "azam", "morocco", "canal+ afrique"]),
        ("Japan", ["japan", "dazn japan", "j sport"]),
        ("India", ["india", "star sports", "sony", "jio"]),
        ("China", ["china"]),
        ("Israel", ["israel"]),
        ("Azerbaijan", ["azerbaijan"]),
        ("Tajikistan", ["tajikistan"]),
        ("Armenia", ["armenia"]),
        ("Albania", ["albania", "tring"]),
    ]
    
    for country, keywords in mappings:
        for kw in keywords:
            if kw in channel_name_lower:
                return country
                
    return "Other / International"

def normalize_team_name(name: str) -> set:
    """Normalize team name by stripping accents, punctuation, and common terms."""
    # Lowercase
    name = name.lower()
    # Strip accents/diacritics
    name = "".join(
        c for c in unicodedata.normalize('NFD', name)
        if unicodedata.category(c) != 'Mn'
    )
    # Remove dots, hyphens, and other punctuation
    name = re.sub(r'[^\w\s]', '', name)
    # Remove common team prefixes and suffixes
    common_terms = {"fc", "sc", "cf", "fk", "utd", "united", "city", "hotspur", "town", "athletic", "atlético", "atletico", "real"}
    words = [w for w in name.split() if w not in common_terms and len(w) > 1]
    if not words:
        words = [w for w in name.split() if len(w) > 1]
    return set(words)

def match_teams(request_team: str, scraped_team: str) -> bool:
    """Intelligently check if the requested team name matches the scraped team name."""
    req_words = normalize_team_name(request_team)
    scr_words = normalize_team_name(scraped_team)
    
    if not req_words or not scr_words:
        return False
        
    # Check if there is any word overlap
    if req_words.intersection(scr_words):
        return True
        
    # Fallback to direct substring matching
    req_norm = " ".join(req_words)
    scr_norm = " ".join(scr_words)
    if req_norm in scr_norm or scr_norm in req_norm:
        return True
        
    return False


def find_matching_match(
    matches: List[Dict],
    home: str,
    away: str,
    date: str
) -> Optional[Dict]:
    """Match both orientations because friendly sources often swap home and away."""
    dated_matches = [match for match in matches if match.get("date") == date]
    for match in dated_matches:
        if (
            match_teams(home, match.get("home", "")) and
            match_teams(away, match.get("away", ""))
        ):
            return match
    for match in dated_matches:
        if (
            match_teams(home, match.get("away", "")) and
            match_teams(away, match.get("home", ""))
        ):
            return match
    return None


def tv_retry_key(home: str, away: str, date: str) -> str:
    teams = sorted([
        " ".join(sorted(normalize_team_name(home))),
        " ".join(sorted(normalize_team_name(away)))
    ])
    return f"{date}|{'|'.join(teams)}"

def scrape_liveonsat() -> List[Dict]:
    """Scrapes liveonsat.com/2day.php and parses all matches into structured data."""
    session = get_http_session()
    response = None
    last_exception = None

    for url in LIVEONSAT_URLS:
        for ua in USER_AGENTS:
            try:
                request_headers = {**HEADERS, "User-Agent": ua, "Host": "liveonsat.com"}
                response = session.get(url, headers=request_headers, timeout=20, allow_redirects=True)
                if response.status_code == 403:
                    continue
                response.raise_for_status()
                break
            except requests.RequestException as exc:
                last_exception = exc
                continue
        if response is not None and response.status_code == 200:
            break

    if response is None:
        raise Exception(f"Failed to fetch LiveOnSat page: {last_exception}")
    if response.status_code == 403:
        raise Exception("LiveOnSat blocked the request (403 Forbidden).")
    response.raise_for_status()

    soup = BeautifulSoup(response.text, "html.parser")
    tables = soup.find_all("table")
    if len(tables) <= 10:
        raise Exception("Failed to find main table in page layout.")
        
    # Main container row 5 holds the matches list
    tr_elements = tables[10].find_all("tr", recursive=False)
    if len(tr_elements) <= 5:
        raise Exception("Main table structure differs from expected layout.")
        
    main_cell = tr_elements[5].find("td")
    if not main_cell:
        raise Exception("Failed to locate main matches data cell.")
        
    tags = [c for c in main_cell.children if c.name]
    if len(tags) <= 2:
        raise Exception("Match listing container div is missing.")
        
    # Tag at index 2 holds the actual match elements
    main_div = tags[2]
    
    current_league = "Other Match"
    current_date_str = ""
    current_date_formatted = None
    matches = []
    
    for tag in main_div.children:
        if not tag.name:
            continue
            
        cls = tag.get("class", [])
        text = " ".join(tag.get_text(" ").split()).strip()
        
        # 1. Date Header
        if "floatAndClearL" in cls:
            if text != "Football":
                current_date_str = text
                current_date_formatted = parse_liveonsat_date(text)
            continue
            
        # 2. League Header
        if tag.name == "div" and not cls:
            current_league = text
            continue
            
        # 3. Match Details block
        if tag.name == "div" and "blockfix" in cls:
            fix_text_div = tag.find("div", class_="fix_text")
            if not fix_text_div:
                continue
                
            teams_div = fix_text_div.find("div", class_="fLeft")
            if not teams_div:
                continue
                
            teams_text = " ".join(teams_div.get_text(" ").split()).strip()
            teams = re.split(r'\s+v[s]?\s+', teams_text)
            if not teams or len(teams) < 2:
                continue
                
            home_team = teams[0].strip()
            away_team = teams[1].strip()
            
            # Kickoff Time
            time_div = tag.find("div", class_="fLeft_time_live")
            kickoff_time = "Unknown"
            if time_div:
                time_text = " ".join(time_div.get_text(" ").split()).strip()
                time_match = re.search(r'(\d{2}:\d{2})', time_text)
                if time_match:
                    kickoff_time = time_match.group(1)
            
            # TV Channels
            channels_div = tag.find("div", class_="fLeft_live")
            channels = []
            if channels_div:
                chan_links = channels_div.find_all("a")
                for link in chan_links:
                    chan_name = " ".join(link.get_text(" ").split()).strip()
                    if chan_name:
                        channels.append(chan_name)
            
            matches.append({
                "date": current_date_formatted or current_date_str,
                "league": current_league,
                "home": home_team,
                "away": away_team,
                "time": kickoff_time,
                "channels": channels
            })
            
    return matches

def scrape_schedule_for_date(target_date: str) -> List[Dict]:
    """Try date-aware and generic sources until the requested day is covered."""
    matches: List[Dict] = []
    errors: List[str] = []
    scrapers: List[Callable[[], List[Dict]]] = [
        lambda: scrape_live_soccertv(target_date),
        scrape_live_football_on_tv,
        scrape_liveonsat
    ]
    for scraper in scrapers:
        try:
            matches = deduplicate_matches(matches + scraper())
            if any(match.get("date") == target_date for match in matches):
                break
        except Exception as exc:
            errors.append(str(exc))
            LOGGER.warning("TV scraper failed for %s: %s", target_date, exc)

    if not matches:
        raise Exception("; ".join(errors) or "No scraper returned match data.")
    return matches


def get_matches_cached(target_date: str) -> List[Dict]:
    """Return one day's schedule, retaining stale daily JSON as an outage fallback."""
    now = datetime.now()
    entry = cache.get(target_date) or load_cache_from_disk(target_date)
    if (
        entry is not None and
        entry.get("expiry") is not None and
        entry["expiry"] > now and
        (
            bool(entry.get("data")) or
            entry["expiry"] <= now + EMPTY_CACHE_DURATION
        )
    ):
        return entry["data"]

    try:
        scraped = scrape_schedule_for_date(target_date)
        save_scraped_dates(scraped)
        refreshed = cache.get(target_date)
        if refreshed is not None:
            return refreshed["data"]

        # A successful scrape that genuinely has no entry for this day is cached too,
        # so repeated requests for the same absent date do not scrape again.
        save_cache_to_disk(target_date, [])
        return []
    except Exception:
        if entry is not None:
            return entry["data"]
        raise


def scraped_match_utc_date(match: Dict) -> Optional[str]:
    """Convert the US-Eastern time exposed by the Jina schedule to UTC."""
    raw_date = match.get("date")
    raw_time = (match.get("time") or "").replace(" ", "").upper()
    if not raw_date or not raw_time or raw_time == "UNKNOWN":
        return None
    for time_format in ("%I:%M%p", "%H:%M"):
        try:
            local = datetime.strptime(
                f"{raw_date} {raw_time}",
                f"%Y-%m-%d {time_format}"
            ).replace(tzinfo=ZoneInfo("America/New_York"))
            return local.astimezone(ZoneInfo("UTC")).isoformat().replace("+00:00", "Z")
        except ValueError:
            continue
    return None

@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "Football TV Channels Scraper API",
        "endpoints": {
            "/tv": "Fetch TV channels broadcasting a specific match. Query parameters: home, away, date.",
            "/schedule": "Fetch the scraped match schedule for one date."
        }
    }


@app.get("/schedule")
def get_schedule(
    date: str = Query(..., description="Schedule date in YYYY-MM-DD format")
):
    """Return a date-aware fallback schedule for clients with limited free APIs."""
    try:
        datetime.strptime(date, "%Y-%m-%d")
    except ValueError:
        raise HTTPException(status_code=422, detail="date must use YYYY-MM-DD format")

    try:
        matches = get_matches_cached(date)
    except Exception as exc:
        raise HTTPException(
            status_code=503,
            detail=f"Failed to fetch schedule data: {str(exc)}"
        )

    return {
        "date": date,
        "matches": [
            {**match, "utcDate": scraped_match_utc_date(match)}
            for match in matches
            if match.get("date") == date
        ]
    }


@app.get("/tv")
def get_tv_channels(
    home: str = Query(..., description="Home team name"),
    away: str = Query(..., description="Away team name"),
    date: str = Query(..., description="Match date in YYYY-MM-DD format")
):
    """Retrieve and group TV channels broadcasting the requested match on the specified date."""
    try:
        datetime.strptime(date, "%Y-%m-%d")
    except ValueError:
        raise HTTPException(status_code=422, detail="date must use YYYY-MM-DD format")

    try:
        matches = get_matches_cached(date)
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Failed to fetch broadcast data: {str(e)}")
        
    target_match = find_matching_match(matches, home, away, date)

    # TV listings are frequently announced shortly before kickoff, while the full
    # daily schedule can still be cached for 15 minutes. Re-scrape an empty result
    # at most once every two minutes, independently for each match.
    retry_key = tv_retry_key(home, away, date)
    retry_allowed_at = empty_tv_retry_after.get(retry_key)
    if (
        (target_match is None or not target_match.get("channels")) and
        (retry_allowed_at is None or retry_allowed_at <= datetime.now())
    ):
        empty_tv_retry_after[retry_key] = datetime.now() + EMPTY_CACHE_DURATION
        try:
            freshly_scraped = scrape_schedule_for_date(date)
            save_scraped_dates(freshly_scraped)
            fresh_for_date = [
                match for match in freshly_scraped
                if match.get("date") == date
            ]
            refreshed_match = find_matching_match(
                fresh_for_date, home, away, date
            )
            if refreshed_match is not None:
                target_match = refreshed_match
            if target_match is not None and target_match.get("channels"):
                empty_tv_retry_after.pop(retry_key, None)
        except Exception as exc:
            LOGGER.warning(
                "Empty TV refresh failed for %s vs %s on %s: %s",
                home, away, date, exc
            )

    if not target_match:
        # Return empty list of channels as fallback (expected by client)
        return {
            "match": f"{home} vs {away}",
            "date": date,
            "channels": []
        }
        
    # Group channels by country/region
    grouped_channels: Dict[str, List[str]] = {}
    channel_countries = target_match.get("channelCountries") or {}
    for chan in target_match["channels"]:
        countries = channel_countries.get(chan) or [get_channel_country(chan)]
        for country in countries:
            if country not in grouped_channels:
                grouped_channels[country] = []
            if chan not in grouped_channels[country]:
                grouped_channels[country].append(chan)
        
    # Convert grouped channels to list of objects required by the Kotlin client
    channel_groups = [
        {"country": country, "channels": chans}
        for country, chans in sorted(
            grouped_channels.items(),
            key=lambda item: (
                0 if item[0] in PRIORITY_N1_RANK else
                1 if item[0] in EUROPE_BROADCAST_REGIONS else 2,
                PRIORITY_N1_RANK.get(item[0], len(PRIORITY_N1_RANK)),
                item[0].lower()
            )
        )
    ]
    
    return {
        "match": f"{target_match['home']} vs {target_match['away']}",
        "date": date,
        "channels": channel_groups
    }

if __name__ == "__main__":
    import os
    import uvicorn

    port = int(os.environ.get("PORT", 8000))
    # Enable reload only when explicitly requested via DEV=1 or DEV=true
    reload_flag = os.environ.get("DEV", "false").lower() in ("1", "true")
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=reload_flag)
