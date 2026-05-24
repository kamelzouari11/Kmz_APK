#!/usr/bin/env python3
"""Test script to count radios matching specific criteria"""

import requests
import json

# RadioBrowser API endpoint
API_BASE = "https://de1.api.radio-browser.info/json"

def count_radios(country_code=None, tag=None, bitrate_min=None):
    """Count radios matching the criteria"""
    
    # Build the search URL
    url = f"{API_BASE}/stations/search"
    
    params = {
        "order": "clickcount",
        "reverse": "true",
        "limit": 10000  # Get all to count
    }
    
    if country_code:
        params["countrycode"] = country_code
    if tag:
        params["tag"] = tag
    if bitrate_min:
        params["bitrate_min"] = bitrate_min
    
    print(f"\n🔍 Searching with criteria:")
    print(f"   Country: {country_code or 'All'}")
    print(f"   Tag: {tag or 'All'}")
    print(f"   Bitrate Min: {bitrate_min or 'All'} kbps")
    print(f"\n📡 API URL: {url}")
    print(f"📋 Parameters: {params}\n")
    
    try:
        response = requests.get(url, params=params, timeout=10)
        response.raise_for_status()
        
        stations = response.json()
        total_count = len(stations)
        
        print(f"✅ Total radios found: {total_count}")
        
        if total_count > 0:
            print(f"\n📊 With limit of 100: Will show {min(100, total_count)} radios")
            
            # Show first 5 as examples
            print(f"\n📻 First 5 radios:")
            for i, station in enumerate(stations[:5], 1):
                print(f"   {i}. {station.get('name', 'N/A')} - {station.get('bitrate', 0)} kbps")
        
        return total_count
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return 0

if __name__ == "__main__":
    # Test case from user
    print("=" * 60)
    print("TEST: Russian Federation + oldies + >=192 kbps")
    print("=" * 60)
    
    count = count_radios(
        country_code="RU",  # Russian Federation
        tag="oldies",
        bitrate_min=192
    )
    
    print("\n" + "=" * 60)
    print(f"RESULT: {count} radios match the criteria")
    print("=" * 60)
