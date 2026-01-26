                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SimpleRADIO", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.7f))
                            IconButton(onClick = { 
                                scope.launch { 
                                    isLoading = true
                                    try {
                                        radioCountries = radioRepository.getCountries().sortedByDescending { it.stationcount }.take(50); radioTags = radioRepository.getTags().sortedByDescending { it.stationcount }.take(50)
                                        val bitrateMax = when (selectedRadioBitrate) { 0 -> 63; 64 -> 127; 128 -> 191; else -> null }
                                        radioStations = radioRepository.searchStations(selectedRadioCountry, selectedRadioTag, radioSearchQuery.takeIf { it.isNotBlank() }, selectedRadioBitrate, bitrateMax)
                                    } catch (e: Exception) {}
                                    isLoading = false
                                }
                            }, modifier = Modifier.weight(0.15f).size(48.dp)) { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                            IconButton(onClick = { 
                                exoPlayer?.stop()
                                (context as? Activity)?.finish() 
                            }, modifier = Modifier.weight(0.15f).size(48.dp)) {
                                Icon(Icons.Default.PowerSettingsNew, "Quitter", tint = Color.Red, modifier = Modifier.size(28.dp))
                            }
                        }
