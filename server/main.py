from fastapi import FastAPI, Query, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import json
import requests
from bs4 import BeautifulSoup
import re
import unicodedata
from datetime import datetime, timedelta
from pathlib import Path
from requests.adapters import HTTPAdapter
from typing import List, Dict, Optional
from urllib3.util.retry import Retry

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

# In-memory cache for scraped matches to ensure maximum performance and respect liveonsat.com's bandwidth
cache = {
    "data": None,
    "expiry": None
}
CACHE_FILE = Path("cache.json")
CACHE_DURATION = timedelta(minutes=15)

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
LIVE_SOCCERTV_PROXY = "https://r.jina.ai/http://www.livesoccertv.com/fr/schedules/"
RETRY_STRATEGY = Retry(
    total=3,
    status_forcelist=[429, 500, 502, 503, 504],
    allowed_methods=["GET", "HEAD", "OPTIONS"],
    backoff_factor=0.5
)

def load_cache_from_disk() -> None:
    if not CACHE_FILE.exists():
        return
    try:
        raw = json.loads(CACHE_FILE.read_text())
        expiry = raw.get("expiry")
        if expiry:
            cache["expiry"] = datetime.fromisoformat(expiry)
        cache["data"] = raw.get("data")
    except Exception:
        pass


def save_cache_to_disk(matches: List[Dict]) -> None:
    try:
        CACHE_FILE.write_text(json.dumps({
            "expiry": (datetime.now() + CACHE_DURATION).isoformat(),
            "data": matches
        }))
    except Exception:
        pass


def get_http_session() -> requests.Session:
    session = requests.Session()
    session.trust_env = False
    session.mount("https://", HTTPAdapter(max_retries=RETRY_STRATEGY))
    session.headers.update(HEADERS)
    return session


load_cache_from_disk()


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


def scrape_live_soccertv() -> List[Dict]:
    """Scrapes LiveSoccerTV schedule content via a markdown proxy."""
    session = get_http_session()
    response = session.get(LIVE_SOCCERTV_PROXY, timeout=30)
    response.raise_for_status()
    text = response.text

    matches = []
    current_date = None

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        line = re.sub(r'^[\*\-\+]\s*', '', line)

        if line.startswith('#'):
            current_date = parse_liveonsat_date(line.lstrip('#').strip())
            continue

        match_line = re.match(
            r'^(?P<time>\d{1,2}:\d{2}\s*(?:am|pm))\[(?P<teams>[^\]]+)\]\([^\)]*\)(?P<rest>.*)$',
            line,
            flags=re.I
        )
        if not match_line or not current_date:
            continue

        teams_text = match_line.group('teams').strip()
        teams = re.split(r'\s+v[s]?\s+', teams_text, flags=re.I)
        if len(teams) < 2:
            continue

        home_team = teams[0].strip()
        away_team = teams[1].strip()
        kickoff_time = match_line.group('time').strip()
        league = "Unknown"

        channels = re.findall(r'\[([^\]]+)\]\([^\)]*\s+"[^"]+"\)', match_line.group('rest'))
        matches.append({
            "date": current_date,
            "league": league,
            "home": home_team,
            "away": away_team,
            "time": kickoff_time,
            "channels": channels
        })

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
        ("Italy", ["italia", "calcio", "sky sport 25", "sky sport italia", "rai"]),
        ("Germany", ["deutsch", "bundesliga", "sky sport premier league de", "dazn 1 bar deutsch", "dazn de"]),
        ("Portugal", ["portugal", "sport tv"]),
        ("Netherlands", ["nederland", "ziggo", "espn 1 nederland"]),
        ("Belgium", ["belgium", "play sports"]),
        ("Balkans", ["bih", "srbija", "hrvatska", "slovenija", "montenegro", "arena premium", "arena sport", "sportklub", "art motion"]),
        ("Scandinavia & Baltics", ["norge", "sverige", "suomi", "danmark", "dansk", "svensk", "baltic", "baltics", "v sport", "viaplay", "voyo", "tv2 play"]),
        ("Turkey", ["türkiye", "turkey", "tivibu", "exxen", "s sport"]),
        ("Bulgaria", ["bulgaria", "diema", "max sport"]),
        ("Romania", ["romania", "digi sport", "prima sport", "primaplay", "voyo pro tv"]),
        ("Ukraine", ["ukraine", "megogo", "setanta sports ukraine"]),
        ("Hungary", ["hungary", "spíler", "match 4"]),
        ("Greece", ["hellas", "greece", "cosmote", "nova sports"]),
        ("USA", ["usa", "nbc", "cnbc", "paramount+", "peacock", "universo", "telemundo", "syfy"]),
        ("Canada", ["canada", "fubo tv canada", "fubotv canada"]),
        ("Australia", ["australia", "stan sport", "optus"]),
        ("MENA (Middle East)", ["mena", "bein sports mena", "ssc", "dubai sports", "abu dhabi"]),
        ("Africa", ["africa", "supersport", "azam", "morocco", "canal+ afrique"]),
        ("Japan", ["japan", "dazn japan", "j sport"]),
        ("India", ["india", "star sports", "sony", "jio"]),
        ("China", ["china"]),
        ("Austria", ["austria"]),
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

def get_matches_cached() -> List[Dict]:
    """Retrieve match schedule from cache if fresh, otherwise scrape."""
    now = datetime.now()
    if cache["data"] is not None and cache["expiry"] > now:
        return cache["data"]
        
    try:
        try:
            matches = scrape_live_soccertv()
        except Exception:
            try:
                matches = scrape_live_football_on_tv()
            except Exception:
                matches = scrape_liveonsat()

        cache["data"] = matches
        cache["expiry"] = now + CACHE_DURATION
        save_cache_to_disk(matches)
        return matches
    except Exception as e:
        # If scraper fails but we have stale cache, return it rather than crashing
        if cache["data"] is not None:
            return cache["data"]
        raise e

@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "Football TV Channels Scraper API",
        "endpoints": {
            "/tv": "Fetch TV channels broadcasting a specific match. Query parameters: home, away, date."
        }
    }

@app.get("/tv")
def get_tv_channels(
    home: str = Query(..., description="Home team name"),
    away: str = Query(..., description="Away team name"),
    date: str = Query(..., description="Match date in YYYY-MM-DD format")
):
    """Retrieve and group TV channels broadcasting the requested match on the specified date."""
    try:
        matches = get_matches_cached()
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Failed to fetch broadcast data: {str(e)}")
        
    # Find match using robust matching criteria
    target_match = None
    for m in matches:
        # Match date
        if m["date"] != date:
            continue
            
        # Match teams
        if match_teams(home, m["home"]) and match_teams(away, m["away"]):
            target_match = m
            break
            
    if not target_match:
        # Return empty list of channels as fallback (expected by client)
        return {
            "match": f"{home} vs {away}",
            "date": date,
            "channels": []
        }
        
    # Group channels by country/region
    grouped_channels: Dict[str, List[str]] = {}
    for chan in target_match["channels"]:
        country = get_channel_country(chan)
        if country not in grouped_channels:
            grouped_channels[country] = []
        grouped_channels[country].append(chan)
        
    # Convert grouped channels to list of objects required by the Kotlin client
    channel_groups = [
        {"country": country, "channels": chans}
        for country, chans in grouped_channels.items()
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
