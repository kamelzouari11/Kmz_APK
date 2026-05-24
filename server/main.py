from fastapi import FastAPI, Query, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import requests
from bs4 import BeautifulSoup
import re
import unicodedata
from datetime import datetime, timedelta
from typing import List, Dict, Optional

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
CACHE_DURATION = timedelta(minutes=15)

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

LIVEONSAT_URL = "https://liveonsat.com/2day.php"

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
    
    for fmt in ("%d %B %Y", "%d %b %Y"):
        try:
            dt = datetime.strptime(f"{date_str} {current_year}", fmt)
            return dt.strftime("%Y-%m-%d")
        except ValueError:
            continue
    return None

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
    response = requests.get(LIVEONSAT_URL, headers=HEADERS, timeout=15)
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
        matches = scrape_liveonsat()
        cache["data"] = matches
        cache["expiry"] = now + CACHE_DURATION
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
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
