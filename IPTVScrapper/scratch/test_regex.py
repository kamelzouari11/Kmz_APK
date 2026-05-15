import re

text = """
🌍Host: http://live.lynxiptv.xyz:80

👤Username: 323685518569

🔑Password: beW1lXQ2mU
"""

clean_text = text.replace("\u00A0", " ").replace("&nbsp;", " ").replace("\r\n", "\n").replace("\r", "\n")

# Improved URL pattern
url_pattern = re.compile(r"https?://[^\s\"'<>(){}^|\\\u27A4\u2705\u2714\U0001F300-\U0001F9FF]+", re.IGNORECASE)
url_matches = list(url_pattern.finditer(clean_text))

print(f"Found {len(url_matches)} URLs")

for i, match in enumerate(url_matches):
    url_str = match.group()
    pos = match.start()
    print(f"URL: {url_str}")
    
    next_url_pos = url_matches[i+1].start() if i + 1 < len(url_matches) else min(pos + 1000, len(clean_text))
    segment = clean_text[pos:next_url_pos]
    print(f"Segment: [{segment}]")

    user_pattern = re.compile(r"(?:User|USER|Username|Login|Account|Utilisateur|👤|👤Username|👤User)\s*[:\u27A4]?\s*([^\s\"'<>]+)", re.IGNORECASE)
    pass_pattern = re.compile(r"(?:Pass|PASS|Password|Pwd|Mot\s*de\s*passe|Motdepasse|🔑|🔑Password|🔑Pass|Password🔑)\s*[:\u27A4]?\s*([^\s\"'<>]+)", re.IGNORECASE)
    
    user_m = user_pattern.search(segment)
    pass_m = pass_pattern.search(segment)
    
    if user_m:
        print(f"User found: {user_m.group(1)}")
    else:
        print("User NOT found")
        
    if pass_m:
        print(f"Pass found: {pass_m.group(1)}")
    else:
        print("Pass NOT found")
