from bs4 import BeautifulSoup
import requests

def test_scraper():
    url = "https://stbstalker.alaaeldinee.com/"
    print(f"Fetching {url}...")
    try:
        response = requests.get(url, timeout=15)
        soup = BeautifulSoup(response.text, 'html.parser')
        
        # Test selectors
        selectors = ["h2 a", ".post-title a", ".entry-title a", "h3.post-title a"]
        all_links = []
        for sel in selectors:
            links = soup.select(sel)
            print(f"Selector '{sel}' found {len(links)} links")
            for a in links:
                href = a.get('href')
                if href and "/202" in href:
                    all_links.append(href)
        
        unique_links = list(set(all_links))
        print(f"\nUnique post links found: {len(unique_links)}")
        for l in unique_links[:5]:
            print(f" - {l}")
            
        if unique_links:
            test_post_url = unique_links[0]
            print(f"\nFetching test post: {test_post_url}...")
            post_res = requests.get(test_post_url, timeout=15)
            post_soup = BeautifulSoup(post_res.text, 'html.parser')
            text = post_soup.get_text()
            print(f"Post text length: {len(text)}")
            
            import re
            url_pattern = re.compile(r"https?://[^\s\"'<>]+")
            urls = url_pattern.findall(text)
            print(f"Found {len(urls)} URLs in post")
            
            mac_pattern = re.compile(r"(?:MAC|Mac|Address)\s*[:\u27A4]?\s*([0-9A-Fa-f:]{17})", re.IGNORECASE)
            macs = mac_pattern.findall(text)
            print(f"Found {len(macs)} MACs in post")
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    test_scraper()
