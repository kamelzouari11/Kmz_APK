import requests
import re

def test():
    url = "https://stbstalker.alaaeldinee.com/"
    print(f"Fetching {url}")
    r = requests.get(url)
    html = r.text
    
    # Find post links using regex if bs4 is missing
    # <h2 class='post-title entry-title' itemprop='name'>
    # <a href='https://stbstalker.alaaeldinee.com/2026/05/smart-stb-emu-pro-01-05-2026.html'>
    post_links = re.findall(r'href=["\'](https?://stbstalker\.alaaeldinee\.com/20\d{2}/\d{2}/[^"\']+\.html)["\']', html)
    post_links = list(set(post_links))
    print(f"Found {len(post_links)} post links")
    for l in post_links[:3]:
        print(f" - {l}")
        
    if post_links:
        post_url = post_links[0]
        print(f"\nFetching post: {post_url}")
        pr = requests.get(post_url)
        post_html = pr.text
        
        # Look for the post body
        # Usually <div class="post-body ...">
        body_match = re.search(r'class=["\']post-body[^>]+>(.*?)<div', post_html, re.DOTALL)
        if body_match:
            body_text = body_match.group(1)
            # Remove HTML tags
            body_text = re.sub(r'<[^>]+>', ' ', body_text)
            print(f"Body text length: {len(body_text)}")
            
            # Test patterns
            url_pattern = re.compile(r"https?://[^\s\"'<>]+")
            urls = url_pattern.findall(body_text)
            print(f"URLs found: {len(urls)}")
            for u in urls[:3]: print(f"  - {u}")
            
            mac_pattern = re.compile(r"(?:MAC|Mac|Address)\s*[:\u27A4]?\s*([0-9A-Fa-f:]{17})", re.IGNORECASE)
            macs = mac_pattern.findall(body_text)
            print(f"MACs found: {len(macs)}")
            for m in macs[:3]: print(f"  - {m}")
        else:
            print("Could not find post body")

if __name__ == "__main__":
    test()
