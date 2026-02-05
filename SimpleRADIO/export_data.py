#!/usr/bin/env python3
import requests
import csv
import json

def fetch_and_export():
    print("Fetching countries from RadioBrowser API...")
    
    # Fetch countries
    countries_url = "https://de1.api.radio-browser.info/json/countries"
    response = requests.get(countries_url)
    countries = response.json()
    
    # Sort by stationcount descending
    countries_sorted = sorted(countries, key=lambda x: x.get('stationcount', 0), reverse=True)
    
    # Write countries CSV
    with open('pays.csv', 'w', newline='', encoding='utf-8') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(['Nom', 'Code ISO', 'Nombre de Stations'])
        for country in countries_sorted:
            writer.writerow([
                country.get('name', ''),
                country.get('iso_3166_1', ''),
                country.get('stationcount', 0)
            ])
    
    print(f"✓ pays.csv created with {len(countries_sorted)} countries")
    
    # Fetch tags
    print("Fetching genres from RadioBrowser API...")
    tags_url = "https://de1.api.radio-browser.info/json/tags?order=stationcount&reverse=true&hidebroken=true"
    response = requests.get(tags_url)
    tags = response.json()
    
    # Filter tags with more than 10 stations
    tags_filtered = [tag for tag in tags if tag.get('stationcount', 0) > 10]
    
    # Write tags CSV
    with open('genres.csv', 'w', newline='', encoding='utf-8') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(['Genre', 'Nombre de Stations'])
        for tag in tags_filtered:
            writer.writerow([
                tag.get('name', ''),
                tag.get('stationcount', 0)
            ])
    
    print(f"✓ genres.csv created with {len(tags_filtered)} genres")
    
    print("\n✅ Done! Files created:")
    print(f"  - pays.csv ({len(countries_sorted)} entries)")
    print(f"  - genres.csv ({len(tags_filtered)} entries)")

if __name__ == "__main__":
    fetch_and_export()
