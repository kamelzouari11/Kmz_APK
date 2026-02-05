
#!/usr/bin/env kotlin

@file:DependsOn("com.squareup.okhttp3:okhttp:4.12.0")
@file:DependsOn("com.squareup.moshi:moshi:1.15.0")
@file:DependsOn("com.squareup.moshi:moshi-kotlin:1.15.0")

import okhttp3.OkHttpClient
import okhttp3.Request
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

data class Country(
    val name: String,
    val iso_3166_1: String,
    val stationcount: Int
)

data class Tag(
    val name: String,
    val stationcount: Int
)

fun main() {
    val client = OkHttpClient()
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Fetch countries
    println("Fetching countries...")
    val countriesRequest = Request.Builder()
        .url("https://de1.api.radio-browser.info/json/countries")
        .build()
    
    val countriesResponse = client.newCall(countriesRequest).execute()
    val countriesJson = countriesResponse.body?.string() ?: "[]"
    
    val countriesAdapter = moshi.adapter<List<Country>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, Country::class.java)
    )
    val countries = countriesAdapter.fromJson(countriesJson) ?: emptyList()
    
    // Sort by stationcount descending
    val sortedCountries = countries.sortedByDescending { it.stationcount }
    
    // Write countries CSV
    File("pays.csv").bufferedWriter().use { writer ->
        writer.write("Nom,Code ISO,Nombre de Stations\n")
        sortedCountries.forEach { country ->
            writer.write("\"${country.name}\",${country.iso_3166_1},${country.stationcount}\n")
        }
    }
    println("✓ pays.csv created with ${sortedCountries.size} countries")

    // Fetch tags
    println("Fetching tags...")
    val tagsRequest = Request.Builder()
        .url("https://de1.api.radio-browser.info/json/tags?order=stationcount&reverse=true&hidebroken=true")
        .build()
    
    val tagsResponse = client.newCall(tagsRequest).execute()
    val tagsJson = tagsResponse.body?.string() ?: "[]"
    
    val tagsAdapter = moshi.adapter<List<Tag>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, Tag::class.java)
    )
    val tags = tagsAdapter.fromJson(tagsJson) ?: emptyList()
    
    // Already sorted by API, but filter > 10 stations
    val filteredTags = tags.filter { it.stationcount > 10 }
    
    // Write tags CSV
    File("genres.csv").bufferedWriter().use { writer ->
        writer.write("Genre,Nombre de Stations\n")
        filteredTags.forEach { tag ->
            writer.write("\"${tag.name}\",${tag.stationcount}\n")
        }
    }
    println("✓ genres.csv created with ${filteredTags.size} genres")
    
    println("\nDone! Files created:")
    println("  - pays.csv (${sortedCountries.size} entries)")
    println("  - genres.csv (${filteredTags.size} entries)")
}

main()
