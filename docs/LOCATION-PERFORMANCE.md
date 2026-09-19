# Paikannuksen viiveen korjaus 18.9.2026

**Toimitus 19.9.2026:** käyttäjä valtuutti testipuhelimen päivityksen sekä commitit ja GitHub-pushin. Sijaintikorjaukset toimitetaan omana committinaan haarassa `ui-pehmea-expressive`, UI-korjaukset erikseen. testipuhelimen uusi debug-asennus sisältää myös alla kuvatun 19.9. tuoreusrajankorjauksen; asennettu APK ja käynnistyminen on varmennettu. testipuhelimeen ei tehty tällä kierroksella päivitystä. Uusin toimitustilanne ja APK-tiiviste: [jatkomuistio](../jatkomuistio).

Käyttäjä havaitsi paikannuksen hitautta erityisesti Wi-Fi-yhteydellä ja lievemmin mobiilissa. Lähdekoodista vahvistui kaksi tarpeetonta odotusta. Muutokset ovat paikallisia version 0.2.2 päälle. Käyttäjän myöhemmällä asennusluvalla testipuhelin ja testipuhelin päivitettiin korjaukseen 18.9.2026. Molempien asennetut APK:t ja käynnistyminen varmennettiin. Asennustiedot ja APK-tiivisteet: [jatkomuistio](../jatkomuistio).

## Syy ja korjaus

`DeviceLocation` pyysi verkko- ja GPS-sijaintia rinnakkain mutta hyväksyi heti vain enintään 50 metrin tarkkuuden. Esimerkiksi tuore 100 metrin verkkosijainti jäi odottamaan GPS:ää jopa 12 sekunnin kokonaisaikarajaan asti. Ennustehaku alkoi vasta tämän jälkeen. Tämä selittää mahdollisen viiveen myös toimivalla internetyhteydellä; Wi-Fi- ja mobiiliverkon todellista osuutta käyttäjän puhelimessa ei mitattu.

- Enintään 200 metrin tarkkuinen tuore sijainti hyväksytään heti. Tämä vastaa jo käytössä olevaa lähimmän paikannimen tarkkuusrajaa.
- Karkeamman ensimmäisen tuloksen jälkeen tarkempaa odotetaan enintään kaksi lisäsekuntia. Jos tarkempi GPS-tulos ehtii saapua, se valitaan. Muutoin käytetään saatua likimääräistä sijaintia ja sen todellista tarkkuutta. Haku ei jatka GPS-tarkennusta tämän jälkeen.
- Ensimmäistä käyttökelpoista tulosta odotetaan edelleen enintään 12 sekuntia; lisäodotus ei ylitä tätä kokonaisrajaa. Vastaanotettaessa yli 30 sekuntia vanhat tai virheelliset koordinaatit hylätään. **19.9. korjaus:** jo hyväksyttyä tulosta ei hylätä uudelleen tarkennusodotuksen lopuksi. Alkuperäinen aikaleima säilytetään, joten palautetun paikan ikä sisältää myös odotuksen. Näin esimerkiksi 29 s vanha kelvollinen verkkosijainti ei muutu virheeksi kahden sekunnin odotuksessa.
- Keskeneräiset Android-pyynnöt perutaan tuloksen valmistuessa tai sovelluksen sulkeutuessa. Taustapaikannusta ei lisätty.

Toinen odotus syntyi siitä, että sama paikannimi haettiin uudelleen jokaisella avauksella. `AppViewModel` säilytti jo lähellä pysyttäessä aiemman nimen, mutta antoi nimipalvelulle silti alkuperäisen nimettömän sijainnin. Nyt se välittää tarkistetun nimen ja sen alkuperäisen ratkaisupisteen `DevicePlaceName`-luokalle. Valmis lähin paikannimi voidaan palauttaa ilman MML- tai Android-geokoodausta, kun tarkka lupa on yhä voimassa.

Nimen säilytys edellyttää aiemman ja uuden sijainnin enintään 200 metrin epävarmuutta sekä enintään 50 metrin etäisyyttä **alkuperäiseen** nimen ratkaisupisteeseen. Ratkaisupistettä ei siirretä peräkkäisillä päivityksillä. Uudet koordinaatit ja niiden aikaleima säilyvät. Liikkuminen rajan ulkopuolelle, puutteellinen nimivastaus tai puuttuva tarkka lupa johtaa tavalliseen nimihakupolkuun. Pelkkä aiemmin ratkaistu kuntanimi ei estä lähimmän paikannimen uutta yritystä.

## Paikallinen varmennus

19.9. lisäregressio varmistaa 29 s vanhan / 800 m tuloksen hyväksymisen 2 s tarkennusodotuksen jälkeen sekä alkuperäisen aikaleiman. Lisäkorjaus sisältyy 19.9. testipuhelimeen asennettuun Expressive-koontiin; sitä ei ole puhelimiin 18.9. asennetuissa APK:issa. Uusin koko työpuun testitulos: [UI-katselmoinnin toinen kierros](REVIEW-UI-EXPRESSIVE-ROUND2.md). Alla olevat 116 testin ja asennusten tiedot kuvaavat 18.9. tilannetta.

Regressiot kattavat 100 ja 200 metrin tuloksen välittömän palautuksen, 800 metrin tuloksen kahden sekunnin lisäodotuksen, tarkemman GPS-tuloksen saapumisen 1,5 sekunnissa, yhden paikannuslähteen, myöhäisen ensimmäisen tuloksen, kokonaisaikarajan, vanhan tuloksen hylkäyksen ja peruutuksen. Nimitestit kattavat verkkohakujen ohituksen, tuoreiden koordinaattien säilymisen, puutteellisen nimen uusinnan, tarkan luvan poistumisen ja alkuperäisen ratkaisupisteen ylittävän liikkumisen. ViewModel-testi tarkistaa samalla säävaroitusten sijaintiodotuksen vapautumisen.

Koko sarja: **116 testiä, 110 läpi, 0 virhettä, 6 erikseen käynnistettävää verkkotestiä ohitettu**. Debug- ja release-käännökset onnistuivat. Molemmissa lint-varianteissa 0 virhettä ja 4 aiempaa päivitysvaroitusta. Tarkistus tehtiin samalla komennolla:

```powershell
$env:SAA_LOCATION_CHECK = '0'
$env:SAA_LIVE_API_TESTS = '0'
$env:SAA_PRICE_CHECK = '0'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --console=plain
```

Tämä on paikallinen ajoitus- ja toimintavarmennus, ei puhelimella mitattu nopeutus. MML:n uuden paikan nimihaku voi yhä kestää enintään kuusi sekuntia; sääpalveluiden verkkohaut kestävät erikseen. Puhelimien Wi-Fi-/mobiilivertailua, commitia, pushia tai julkaisua ei tehty. Myöhemmin luvitettu asennus ja käynnistystarkistus on dokumentoitu handoffiin. Projektin juuren aiemmat APK:t säilytettiin; korjatut paketit ovat erikseen nimillä `Saa-Sahko-0.2.2-paikannuskorjaus-release.apk` ja `Saa-Sahko-0.2.2-paikannuskorjaus-debug.apk`.

Androidin pyyntö- ja peruutussopimus: [LocationManager.getCurrentLocation](https://developer.android.com/reference/android/location/LocationManager#getCurrentLocation(java.lang.String,%20android.os.CancellationSignal,%20java.util.concurrent.Executor,%20java.util.function.Consumer%3Candroid.location.Location%3E)).
