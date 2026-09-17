# 0.2.2:n toinen katselmointi ja korjaukset — 17.9.2026

Tämä on toisen kierroksen historiallinen tilanne. Uusin korjausraportti: [REVIEW-0.2.2-ROUND3.md](REVIEW-0.2.2-ROUND3.md). Kolmas kierros korvaa muun muassa alla kuvatun TTL-odotuksen ja paikannimen säilytystavan.

Lähtötila: puhdas työpuu, HEAD `7cd2461`, toiminnalliset muutokset commitissa `2073661`. katselmoinnin raportti tarkistettiin lähdekoodista, tuotantopolkujen regressiotesteillä sekä debug/release-käännöksillä ja lintillä. Korjattu koodi ja testit ovat commitissa **`6a761fa`**. Alla oleva tilanne kuvaa tämän korjauskierroksen lopputulosta. Fyysisiä puhelimia tai Pebbleä ei käytetty.

## Uudet 15 kohtaa

Polut ovat `app/src/main/java/fi/omasaasahko/`-hakemiston alla, ellei muuta mainita.

| # | Arvio | Korjaus ja rajaus |
|---|---|---|
| 1–2 | P1, vahvistettu; sama juurisyy | `AppViewModel` päättää odotuksen virheessä, peruutuksessa, sulkemisessa ja tyhjentymisessä. `WarningService` tallentaa `pendingSince`-ajan ja sallii odotuksen enintään 2 minuuttia. Vanha lipputila ilman aikaa sekä kellon siirtyminen taakse purkavat odotuksen. Prosessin kuolema ei voi jättää ilmoituksia pysyvästi mykäksi. Myös varoitusten peruutukset käsitellään taas odotuksen päätyttyä. |
| 3 | P2, vahvistettu | `Place.origin` erottaa DEVICE-, SELECTED- ja UNKNOWN-paikat. `CachedData.devicePlace` palauttaa viimeisen laitepaikan erikseen. Suosikin tai alkuperältään tuntemattoman vanhan välimuistin ennustetta ei palauteta omana sijaintina. Varoitusasetusten seurantapaikka tulee vain `devicePlace`-kentästä. |
| 4 | P2, vahvistettu | `useCurrentLocation()` peruu suosikin keskeneräisen säähaun, palauttaa laitepaikan ja tyhjentää suosikin ennusteet heti, myös jos seuraava paikannus epäonnistuu. |
| 5 | P2, vahvistettu | `MainActivity` luo application-contextin ja `WarningService`-olion paikallisiin muuttujiin. ViewModelin callbackit kaappaavat palvelun, eivät Activityn `applicationContext`-getterin. |
| 6 | P2, vahvistettu | `WarningsScreen` näyttää sekä yleisen että päiväkohtaisen tyhjän tilan myös valitulle paikalle ilman sijaintilupaa. |
| 7 | P2, vahvistettu | Paikannuksen tunnetut käyttäjävirheet välitetään `LocationFailure`-tyypillä: esimerkiksi sijaintipalvelun poiskytkentä säilyttää täsmällisen ohjeen. Tuntemattomien poikkeusten sisältöä ei näytetä käyttäjälle. Yleisviesti ei oleta varoitusilmoitusten olevan käytössä. |
| 8 | P2, osittain vahvistettu | Nimi todella vilkkui ja saattoi heikentyä nimihakujen epäonnistuessa. Viimeinen ratkaistu laitepaikan nimi säilytetään enintään 50 metrin siirtymässä, jos uuden fixin tarkkuus on enintään 200 m. Tämän ulkopuolelle nimeä ei siirretä. Uusi onnistunut nimihaku saa aina vaihtaa nimen. Väite automaattisesta uudesta Android-hälytyksestä oli liian vahva: `setOnlyAlertOnce` oli jo käytössä; Pebblen uudelleenhälytystä ei todennettu. |
| 9 | P2, vahvistettu | Aikaikkuna koskee `checkBackground()`-polkua. Kytkimen päälle laittamisen `check()` hakee ja käsittelee hinnat myös klo 18. Molemmissa säilyvät lupa-, täydellisen hintapäivän ja kerran päivässä -ehdot. |
| 10 | P2, vahvistettu | `PlacePicker` on nyt päivityslaatikon sisarelementti. Sääkoostumus säilyy sen alla, joten avattu päivä ja muut muistettavat valinnat säilyvät. Haku peittää sään saavutettavuustiedot ja estää päivityspainikkeen; hakulistan ele ei käynnistä sää- tai GPS-hakua. |
| 11 | P2, osittain vahvistettu | Toinen verkkolataus syntyi, jos ensimmäinen ehti valmistua ennen nimeä. Aktiivinen `fetch` esti sen muussa tapauksessa, joten latauksia ei tullut aina kahta. `place()` tekee nyt vain uudelleenkohdistuksen. CAP-verkkohaku kuuluu käynnistys-/päivitysloopille. Uudelleenkohdistus ajetaan IO-säikeessä, ja poikkeukset käsitellään. |
| 12 | P3, tehokkuusparannus vahvistettu | Ajastimen koko päivän tarkistukset poistettiin `setNextScheduleTimeOverride`-ajoituksella. Seuraava ajo on hakuikkunassa aikaisintaan 15 minuutin päästä; ikkunan jälkeen tai ilmoituksen valmistuttua seuraavana päivänä klo 14. Ajoitus käyttää Suomen kalenteria myös kellonsiirtopäivinä. Työn sisäinen päivitys käyttää samaa työ-ID:tä, joten se ei voi luoda käyttäjän peruuttamaa työtä uudelleen. Myöhästyneen työn kellonaikaportti säilyy. Klo 16 jälkeistä taustahakua ei lisätty: käyttäjä nimenomaisesti rajasi haut klo 14–16:een. |
| 13 | P3, vahvistettu | Varsinainen tuotantonimeäminen siirrettiin `DevicePlaceName.describe()`-polkuun, jota `DeviceLocation.describe()` kutsuu ja regressiotestit ajavat. Testeissä käsitellään myös aikakatkaisut, peruutus, Androidin eri kaupunginosat, kunnan kopio, väärän kunnan ehdokas ja tarkkuus/lupa. Vain testeissä käytetyt `PlaceNames.resolve`, `PlaceNames.parse` ja `MmlPlaceNames.reverse` poistettiin. Käyttöliittymä käyttää yhteistä etäisyysrajaa, Geocoderilta pyydetään yksi tulos. |
| 14 | P3 / kohdan 3 rakenne, vahvistettu | `origin` ja `nameResolved` tallennetaan yhteisellä Place-JSON-muunnoksella. Välimuistin ohjaus ei riipu näkyvän paikannimen sanamuodosta. `CURRENT_LOCATION_NAME` on yhteinen teksti; vanhan välimuistin migraatio tunnistaa sen yhdessä paikassa. |
| 15 | P3, vahvistettu | `jatkomuistio`:n seuraavan istunnon ohje päivitettiin nykyisiin committeihin ja molempiin 0.2.2-dokumentteihin. Historialliset 0.2.1:n tiedot on erotettu nykytilasta. |

Jos uusi paikannus epäonnistuu, seuranta jatkuu viimeksi saaduille laitekoordinaateille. Tätä ei esitetä uutena onnistuneena paikannuksena: käyttöliittymä näyttää virheen ja viimeisen sijainnin ajan. Taustapaikannusta ei lisätty.

## Edellisen kierroksen 15 kohdan tarkistettu tila

| # | Tila |
|---|---|
| 1 | Avoin: 10 sekunnin testi käyttää edelleen coroutine-viivettä ilman omaa wake lockia. Aiempi langaton ADB -koe ei todista toimintaa CPU:n ollessa keskeytettynä. |
| 2 | Korjattu: ensimmäisen Geocoder-osoitteen locality/subAdminArea/subLocality-varavaihtoehdot. |
| 3 | Korjattu tuotantopolussa; nyt myös tuotantopolun testi kattaa seuraavan sopivan nimiehdokkaan. |
| 4 | Korjattu: karkea verkkopaikannus ei enää heti peruuta GPS:ää. |
| 5 | Korjattu: koordinaatit ja ennustepyyntöjen käynnistys eivät odota paikannimeä. |
| 6 | Korjattu pääosin: fin/swe/muu-järjestys, MML:n kunta ensisijaisena. Nimipisteet eivät edelleenkään todista hallinnollisia rajoja. |
| 7 | Korjattu: kaupunginosaa ei poimita myöhemmästä osoitevastauksesta. |
| 8 | Korjattu: trimmaus ja tyhjän paikallisen avaimen ympäristövaravaihtoehto. |
| 9 | Ei muutettu: aiemmin hyväksytty MML-avaimen APK-upotus säilyy. Julkisen APK-jakelun avaineristys on edelleen erillinen päätös. Avainta ei tulostettu tai viety Gitiin. |
| 10 | Osittain: MML-client alustetaan IO-säikeellä, mutta yhteistä HTTP-siltaa ei ole vielä erotettu. Tämä on ylläpidettävyyttä koskeva jatkotyö. |
| 11 | Korjattu jo `2073661`: sekä `Place.toJson()` että `placeFromJson()` ovat yhteisiä Repositorylle ja WarningServicelle. Raportin rajaus vain lukupuolen korjaukseen oli väärä. |
| 12 | Ei vahvistettua toimintavikaa: Activity rekisteröi kanavat ennakkoon, mutta työntekijöiden ilmoituspolut rekisteröivät tarvittavan kanavan myös ilman Activityä. Application-siirto on mahdollinen rakenneparannus. |
| 13 | Oletusarvoja koskeva havainto on oikea, mutta niitä ei lasketa toiminnalliseksi Pebble-korjaukseksi. Aiempi dokumentaatio kertoi jo tämän rajan. |
| 14 | Avoin ylläpidettävyysasia: `DelayedNotificationTest`-nimi ja käyttöliittymän testipainikeosuudet säilyvät. |
| 15 | Korjattu: HTTP-pyynnöt kerätään ja väitteet suoritetaan testisäikeessä, myös size/lang. |

## Varmennus

```powershell
$env:SAA_LOCATION_CHECK = '1'
$env:SAA_LIVE_API_TESTS = '1'
$env:SAA_PRICE_CHECK = '0'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --console=plain
```

**95 testiä: 94 läpi, 0 epäonnistunutta, 1 ohitettu** (erillinen Nord Pool -hintavertailu). MML-, FMI-, Open-Meteo- ja Elering-verkkotestit mukana. FMI:n asemat julkisissa vertailupisteissä: Porvoo Harabacka 3,3 km ja Inari Kaamanen 28,4 km; tuoreet havainnot ja koordinaattien säilyminen tarkistettu.

Kohdennetut uudet testit: `LocationRecoveryTest`, `DevicePlaceNameTest`, `WarningRecoveryTest`, `PriceScheduleTest` sekä lisäykset `WarningsTest`, `PriceAlertsTest` ja `ScreenTest`. WorkManager-testissä todetaan tallennettu seuraava ajoaika ja peruutetun työn pysyminen peruutettuna. Käyttöliittymätesti kattaa hakueleen eristyksen, ennustepäivän säilymisen ja suosikin molemmat tyhjät varoitustilat. `place-search.png` tarkistettiin visuaalisesti.

Koko ajon jälkeen poistettiin lintin osoittama tyhjä `super.onCleared()`-kutsu ja uusittiin molemmat APK-käännökset sekä lint. Tämä ei muuta toimintaa. Fyysistä akun-/Doze-mittausta, kahden puhelimen sijaintivertailua tai Pebble-koetta ei tehty. Myös Androidin rajoitusten vuoksi kokonaan ohitettu klo 14–16 -ikkuna voi jättää taustailmoituksen toimittamatta; sovelluksen avaaminen tai ilmoituskytkin voi hakea tiedot myöhemminkin.

Lopulliset debug- ja release-lintit: **0 virhettä, 4 aiempaa riippuvuuspäivitysvaroitusta kummassakin**. Allekirjoitetun `Saa-Sahko-0.2.2-release.apk`-paketin SHA-256: `1608E2184DEF8DC8610B53A15E5BE798AB207BB92D6E3BC24BA7920C1BEE891A`. Allekirjoitus vastaa pysyvää release-varmennetta. Versio on edelleen 0.2.2 / versionCode 6. Paketti on vain paikallinen, eikä tällä kierroksella tehty GitHub-releasea tai asennuksia. Käyttäjä pyysi lähdekoodin ja raportin commitointia ja pushia GitHubiin.

Ajastusratkaisu perustuu [AndroidX:n PeriodicWorkRequest-toteutuksen dokumentaatioon](https://github.com/androidx/androidx/blob/androidx-main/work/work-runtime/src/main/java/androidx/work/PeriodicWorkRequest.kt): käynnissä oleva työ voi siirtää seuraavaa ajoa ilman itsensä peruuttamista. Ajoitus on silti Androidin taustarajoitusten alainen.
