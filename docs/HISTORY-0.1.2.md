# Sää & Sähkö — jatkotilanne 16.9.2026

## Toimitettu

Android-versio **0.1.2** (`versionCode=3`), sovellustunnus `fi.omasaasahko`. Debug-APK päivitettiin testipuhelimeen Wi-Fi-ADB:n kautta. Laite tunnistettiin uudelleen mallin perusteella. Asennus tehtiin `adb install -r` -päivityksenä, ja asennetun APK:n SHA-256 vastaa paikallista tiedostoa. Muihin ADB:ssä oleviin laitteisiin ei tehty muutoksia.

Sää ja Pörssi-sähkö ovat ylävälilehdillä. Sovellus avautuu säähän ja käyttää nykyistä sijaintia vain etualalla. FMI ja Open-Meteo ovat rinnakkain: nykyhetken ennuste, erillinen lähialueen havainto, 24 tunnin lista, lähdettä vaihtava sadekaavio sekä avattavat viikon päivät. Puhelimen vaalea/tumma tila ja dynaamiset korostusvärit ovat käytössä.

Version 0.1.2 muutokset:

- Sähkösivun hintavärit nykyhinnassa, päivätilastoissa, kaaviossa ja hintalistassa. Alle 5,000 vihreä, 5,000–9,999 keltainen, 10,000–14,999 oranssi, 15,000–19,999 punainen, vähintään 20,000 violetti (snt/kWh). Tumma/vaalea paletti, sanalliset tasot ja väriselite.
- Väriluokka määräytyy näytettävän, kolmeen desimaaliin pyöristetyn hinnan mukaan ja seuraa ALV- sekä vartti/tuntivalintaa. Puuttuvat hinnat ovat neutraaleja. Valittu kaaviopylväs säilyttää hintavärinsä.
- Viimeisin hintahaku näkyy heti sähkösivun yläosassa.
- Käyttäjän pyynnöstä hintavarmennus Nord Poolin virallisen dataportaalin kanssa: 192/192 varttia täsmäsi ja 480 hintanäyttöä varmennettiin sovelluksen laskennan läpi. Katso `docs/PRICE-VERIFICATION.md`.
- `OfficialPricesLiveTest` on erikseen ajettava verkkotesti (`SAA_PRICE_CHECK=1`). Sovelluksen ajonaikainen hintalähde on edelleen Elering. Jatkuvaa kahden lähteen ristiintarkistusta ei ole lisätty eikä tulevien palveluvastausten virheettömyyttä luvata.

Version 0.1.1 ominaisuudet säilyvät:

- Siniset FMI-kortit ja turkoosit Open-Meteo-kortit, moniväriset sääsymbolit sekä lämminsävyinen aurinkokortti molempiin teemoihin.
- Etusivun molemmat sääkortit ovat yhtä korkeat, vaikka tietomäärä, havainto, virheteksti tai saatavuus eroavat.
- Sijainnin ja päivämäärän yhteydessä automaattisesti päivittyvä **Nyt HH.mm**. Korttien oma **Ennuste HH.mm** säilyy.
- Auringonnousu ja -lasku FMI:n `Sunrise`/`Sunset`-parametreista; varalähteenä Open-Meteo. Käytetty lähde näkyy kortissa.
- Viikkonäkymän plus/miinus on korvattu avautuvalla/sulkeutuvalla nuolella ja saavutettavalla tilakuvauksella.
- **ALV 25,5 %** -kytkin, oletuksena päällä, tallennetaan asetuksiin. Muutos vaikuttaa nykyhintaan, vartteihin/tuntikeskiarvoihin, päivätilastoihin ja kaavioon. Kaikissa hinnoissa on edelleen kolme desimaalia pilkulla.
- Oma adaptiivinen aurinko–pilvi–salama-kuvake sinisellä liukuväritaustalla. Erillinen yksivärinen vektori Androidin teemakuvakkeille.

Sähkön alkuperäiset ominaisuudet säilyvät: Suomen tämän päivän ja huomisen hinnat, muistettava vartti/tunti-valinta, painettava kaavio ja aikaluettelo, negatiiviset hinnat, puuttuvat vartit ja 23/25 tunnin päivät.

## Viimeisin varmennus

Komento: `SAA_PRICE_CHECK=1` ympäristömuuttujalla `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`.

- **29 testiä läpi, 1 ohitettu**: 28 paikallista testiä ja uusi virallinen hintavertailu onnistuvat. Sääpalveluiden erillinen live-testi oli tässä ajossa pois päältä; se varmennettiin versiossa 0.1.1.
- Hintavertailu 16.9.2026 klo 20.16.57 Suomen aikaa: 192 FI-varttia täsmälleen samat Eleringissä ja Nord Poolissa päiville 16.–17.9.; 480 vartti-/tuntinäyttöä ALV päällä ja pois. Nord Poolin Keski-Euroopan päivänraja huomioitiin UTC-jaksojen avulla.
- Käyttöliittymätesti varmistaa, että hintataso vaihtuu ALV-kytkimen ja tuntikeskiarvovalinnan mukana. Molempien teemojen hintasivu, kaavio, hintalista ja suuri fontti tarkastettiin kuvista.
- Debug-käännös onnistui. Lint: **0 virhettä**, neljä aiempaa riippuvuus/Gradle-päivitysilmoitusta.
- Debug 0.1.2 asennettiin testipuhelimeen päivityksenä. Asennetun APK:n SHA-256 vastasi paikallista tiedostoa. Muita laitteita ei muutettu.
- Testipuhelimen sähkösivu tarkastettiin oikealla datalla: 16.9. klo 20.15–20.30 **2,369 snt/kWh, ALV pois**, vihreä kortti ja Halpaa-merkintä. Arvo täsmää viralliseen raakadataan 23,69 EUR/MWh. Käyttäjän ALV-valintaa ei muutettu. Haettu-aika näkyi 20.17 ja puhelimen teema säilyi.
- Tämän päivän kaikki hinnat ovat alle 5 snt/kWh myös ALV:n kanssa, joten koko päivä on oikein vihreä. Testien esikatseludata kattaa kaikki viisi hintaluokkaa, eikä sitä käytetä varsinaisessa sovelluksessa.
- Kuvat: `app/build/screenshots/electricity-*.png` ja `app/build/device-screenshots/electricity-0.1.2.png`. Hintavertailun raportti: `app/build/test-results/testDebugUnitTest/TEST-fi.omasaasahko.OfficialPricesLiveTest.xml`.
- Fyysisen laitteen tarkistus kattoi asennuksen ja sähkösivun. Vaihtimien toiminta ja molemmat teemat varmennettiin paikallisissa käyttöliittymätesteissä; laiteasetuksia ei muutettu.

APK: `app/build/outputs/apk/debug/app-debug.apk`

SHA-256: `CB1C4437838D2B12846D4F01D253B2454ADB326C0654D2D8E758E4A58A0060E6`

## Huomiot jatkajalle

- `README.md` sisältää build-ohjeet, arkkitehtuurin ja rajapintalinkit. `validate.ps1` ajaa paikallisen käännöksen, testit ja lintin. Live-testi on normaalisti pois päältä ja vaatii ympäristömuuttujan.
- FMI:n WFS ei hyväksy havaintokyselyn kellonajoissa sekunnin murto-osia. Aikaleimat typistetään kokonaisiin sekunteihin.
- FMI:n observation/simple ei tue ennustehaun `latlon`-parametria. Havaintoihin käytetään `bbox`-rajausta ja valitaan lähin lämpötilan palauttanut asema sen koordinaattien perusteella; eri asemien rivejä ei saa sekoittaa.
- FMI:n aurinkoaikojen tiivis merkkijono tulkitaan UTC-ajaksi ja ryhmitellään tapahtuman paikallisen päivämäärän mukaan. WFS-näytteen paikallinen päivä voi olla eri, joten siihen ryhmittely olisi väärin.
- Sääpalveluiden ei tarvitse palauttaa samoja kenttiä tai yhtä pitkää jaksoa. FMI:n sadeprosenttia/UV-arvoa ei täytetä toisesta palvelusta. Vajaat päivät merkitään.
- `PriceSlot.centsPerKwh` sisältää valitun verotilan mukaisen hinnan. Alkuperäiset hinnat säilyvät verottomina välimuistissa. ALV-asetus on SharedPreferences-avain `includeVat`, oletusarvo `true`.
- Esimerkkidata on vain debug-esikatseluiden ja testien käytössä. Sovelluksen varsinaiseen näkymään ei syötetä näytedataa.
- Paikkatiedot ja palveluvastaukset tallentuvat sovelluksen sisäiseen välimuistiin. Ei taustapaikannusta, omaa palvelinta tai analytiikkaa. Varmuuskopiointi/laitesiirto estetty.
- Tämä hakemisto alkoi neljästä Supersää-referenssikuvasta. Kuvat säilytettiin. Aiempia omia projekteja ei muutettu.
- Git-repoa/committia/pushia ei tehty. Ei release-allekirjoitusta tai julkaisua.

Tutka, varoitukset, hintahistoria, widgetit ja ilmoitukset ovat sovitun ensimmäisen version ulkopuolella.
