# Paikkahaku, FMI-asemat ja ilmoitusten sijainti — 0.2.2

Uusin paikallinen jatko: [paikannuksen viiveen korjaus 18.9.2026](LOCATION-PERFORMANCE.md). Asennetun 0.2.2:n aiempi korjaus- ja varmennustilanne: [REVIEW-0.2.2-ROUND3.md](REVIEW-0.2.2-ROUND3.md). Alla olevat aiemmat testimäärät ja APK-tiiviste kuvaavat niitä edeltäviä käännöksiä.

17.9.2026. VersionName 0.2.2, versionCode 6. Toiminnalliset muutokset ovat commitissa `2073661`, joka sisältää myös aiemmat paikalliset 0.2.1-muutokset. Käyttäjä pyysi lähdekoodin ja dokumentaation commitoinnin sekä pushin GitHubin main-haaraan. Käyttäjä valitsi lähimmän FMI-aseman havainnot, katseltavan paikan koordinaattien ennusteet ja ilmoitusten seuraavan puhelimen sijaintia.

## Käyttäjälle näkyvä toiminta

- Sovellukseen palaaminen käynnistää aina uuden paikannuksen. Aiempi 15 minuutin raja saattoi pitää Espoon tiedot Porvoossa, vaikka sovellus avattiin matkan jälkeen. Sähköhinnatkin päivitetään avauksella.
- Verkko- ja GPS-paikannusta odotetaan enintään 12 sekuntia. Paikallisessa 18.9. korjauksessa enintään 200 metrin tarkkuudella jatketaan heti; karkeamman ensimmäisen tuloksen jälkeen tarkempaa odotetaan korkeintaan kaksi lisäsekuntia kokonaisaikarajan sisällä. Vastaanotettaessa enintään 30 sekuntia vanha sijainti hyväksytään. Paikallinen 19.9. korjaus säilyttää hyväksytyn tuloksen myös tarkennusodotuksen lopussa ja palauttaa alkuperäisen aikaleiman. Alkuperäinen asennettu 0.2.2 käytti 50 metrin rajaa ja saattoi odottaa GPS:ää koko 12 sekunnin ajan.
- Ennustepyynnöt alkavat koordinaattien saavuttua. MML-paikannimi ja Androidin varahaku tehdään erikseen. Molemmissa puhelimissa käytetään samaa MML-nimiaineistoa, joten Androidin erilaiset kaupunginosanimet eivät määrää ensisijaista otsikkoa.
- MML:n lähimmän nimipisteen kunta toimii otsikkona. Nimipisteen nimi näytetään erikseen. Piste ei todista hallinnollista rajaa; kuntarajojen lähellä nimipisteen kunta voi poiketa koordinaatin kunnasta. GPS-ero voi edelleen vaihtaa lähimmän pisteen kahden nimen rajalla.
- MML-haun säde on 5 km. Yli 750 metrin etäisyys nimipisteeseen näytetään. Tarkempi nimi vaatii tarkan sijaintiluvan ja enintään 200 metrin ilmoitetun epävarmuuden. Karkealla sijainnilla näytetään tarkkuus ja kunta; tarkkaa nimeä ei arvata. Ilman kelvollista MML-vastausta käytetään Androidin ensimmäisen osoitteen kuntaa/aluevara-arvoa.
- **Hae paikka / suosikit** hakee kunnat ja paikannimet MML:stä, myös ruotsinkielisillä hakusanoilla. Suomenkielistä kirjoitusasua suositaan; pelkkä ruotsinkielinen nimi hyväksytään. Hakutulos sisältää kunnan ja paikkatyypin. Tähti tallentaa tai poistaa suosikin (enintään 50). Suosikit säilyvät paikallisessa tallennuksessa. **Nykyinen sijainti** palaa puhelimen paikannukseen.
- Sää ja Varoitukset-välilehden kortit voivat näyttää haettua paikkaa. Ilmoitusasetuksissa näkyvä seurattava paikka tulee erikseen puhelimen paikannuksesta. Suosikin valinta ei kirjoita ilmoitusten sijaintia.

## FMI-havainnot

Ennusteet käyttävät alkuperäisen katseltavan paikan koordinaatteja. FMI-havainnot haetaan `fmi::observations::weather::multipointcoverage`-kyselyllä, jonka vastauksessa aseman tunniste, nimi, koordinaatit ja havainnot kulkevat yhdessä. Tämä havaintokysely käyttää `bbox`-aluetta; `latlon` ei toiminut testatussa palvelussa.

Hakulaatikko kattaa ensin 25 kilometrin ympyrän, tarvittaessa 100 ja 300 km. Asema hyväksytään vasta, kun se on ympyrän sisällä: tällöin laatikon ulkopuolella ei voi piillä lähempää asemaa. Valitaan lähin asema, jolla on enintään 90 minuuttia vanha lämpötilahavainto. Saman aseman viimeisin kelvollinen näyte määrää myös tuulen; eri asemien puuttuvia arvoja ei yhdistetä. Tulevaisuuden näytteet hylätään. Aseman nimi, etäisyys, lämpötila, havaintoaika ja tuuli näkyvät FMI-kortissa. Havaintohaku saa käyttää yhteensä enintään 15 sekuntia; sen epäonnistuminen ei hylkää ennustetta.

## Ilmoitukset

Uuden paikannuksen alkaessa vanhan paikan uusien varoitusten lähettäminen estetään. Tuoreet koordinaatit tallennetaan heti; nimi valmistuu erikseen. Jos sovellus suljetaan nimen haun aikana, jo saadut tuoreet koordinaatit sallitaan taustavaroituksille. Jos tuoretta sijaintia ei saada, käyttöliittymä näyttää paikannuksen virheen ja seuranta jatkuu viimeksi saaduille laitekoordinaateille. Odotus puretaan virheessä, peruutuksessa ja sulkemisessa; prosessin kuollessa muistin odotus katoaa heti. Odotus kuuluu sen aloittaneelle palveluinstanssille. Alkuperäisen 0.2.2:n pysyvä estolippu korjattiin katselmointikierroksella. Jo näkyvää ilmoitusta ei peruta pelkän paikannuksen alkamisen takia.

Tuoreen sijainnin jälkeen viimeinen tuore CAP-snapshot arvioidaan uudelleen. Verkkosyöte haetaan sovelluksen käynnistys-/päivitysloopissa, ei erikseen jokaisesta paikan muutoksesta. Jo näkyvän, saman alueellisen varoituksen paikkateksti päivitetään hiljaisesti; käyttäjän jo poistamaa samansisältöistä varoitusta ei tuoda takaisin. Taustalla ei paikanneta, joten matkalla sovellus on avattava.

Hintailmoituksen WorkManager-tarkistusväli lyhennettiin 30 minuutista 15 minuuttiin; olemassa oleva työ päivittyy sovelluksen avauksella. Käyttäjän lisäpyynnöstä taustaverkkopyyntö aloitetaan vain **klo 14.00 ≤ Suomen aika < 16.00**. Kellonaika tarkistetaan työn todellisella käynnistyshetkellä, myös Androidin viivästyttämässä uusintayrityksessä. Muina aikoina mahdollinen viivästynyt WorkManager-työ palautuu ilman hintaverkkopyyntöä tai Repositoryn alustamista. Katselmointikorjaus asettaa seuraavan ajoajan suoraan klo 14:ään, joten työtä ei normaalisti ajeta vartin välein yön ja aamun aikana. Kytkimen käyttäjän käynnistämä tarkistus toimii aikaikkunan ulkopuolellakin. Ilmoituksen jälkeen ei haeta saman toimituspäivän hintoja uudelleen. Sovelluksen avaaminen ja Päivitä-painike toimivat kaikkina kellonaikoina, ja niiden jo hakema täydellinen hintatieto voi muodostaa ilmoituksen myös hakuikkunan jälkeen. Europe/Helsinki huomioi kesäajan automaattisesti. Säävaroitusten käyttäjän valitsema 15/30/60 minuutin väli säilyy. Laitteiden kello 14.55 / 15.03 ero voi syntyä itsenäisistä WorkManager-ajoista ja Androidin taustarajoituksista. Tarkkaa syytä ei vahvistettu laitelokeista. Samanaikaista tai minuutilleen täsmällistä toimitusta ei luvata.

## Alkuperäisen klo 14–16 -rajauksen varmennus, 17.9.2026 (historia)

Sama debug/release-, unit test- ja lint-komento ajettiin aikarajauksen jälkeen verkkovalinnoilla `SAA_LOCATION_CHECK=0`, `SAA_LIVE_API_TESTS=0`, `SAA_PRICE_CHECK=0`. **79 testiä: 73 läpi, 0 epäonnistunutta, 6 erillistä verkkotestiä ohitettu.** Molemmat APK:t kääntyivät, molemmat lint-ajot: 0 virhettä ja 4 aiempaa päivitysvaroitusta.

Uudet testit kutsuvat varsinaista taustatarkistusta ja laskevat verkkohakukutsut ennen klo 14, tasan klo 14, klo 15.59.59, tasan klo 16 sekä yöllä. Mukana talvi, kesä, molemmat kellonsiirtopäivät ja puhelimen Suomen ajasta poikkeava aikavyöhyke. Ilmoituksen jälkeen haku pysähtyy ja sallitaan seuraavana päivänä vasta klo 14. Etualalla jo haetun tiedon ilmoitus toimii myös klo 18. Käyttöliittymän uusi ajoitusteksti varmennettiin testisarjassa.

Tässä aiemmassa versiossa ajastin teki lyhyitä paikallisia tarkistuksia aikaikkunan ulkopuolellakin. Uudempi katselmointikorjaus siirtää seuraavan ajoajan klo 14:ään; ks. [katselmointiraportti](REVIEW-0.2.2.md). Akunkulutuksen muutosta ei ole mitattu laitteilla.

## Aiempi saman päivän varmennus ennen aikarajausta

```powershell
$env:SAA_LOCATION_CHECK = '1'
$env:SAA_LIVE_API_TESTS = '1'
$env:SAA_PRICE_CHECK = '0'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --console=plain
```

**76 testiä: 75 läpi, 0 epäonnistunutta, 1 ohitettu.** Ohitettu testi on erillinen Nord Pool -hintavertailu. Molemmat APK-variantit kääntyivät. Molemmissa lint-ajoissa 0 virhettä ja 4 aiempaa riippuvuus-/Gradle-päivitysvaroitusta.

- Paikalliset regressiot: Espoo → Porvoo alle 15 minuutissa, karkea verkko ennen tarkkaa GPS:ää, GPS:n aikakatkaisu, hidas ja myöhästynyt paikannimi, sulkeminen kesken nimen haun, suosikin ja ilmoituspaikan riippumattomuus, hakukilpailu, suosikin pysyvyys ja ilmoituksen paikkatekstin hiljainen korjaus.
- MML:n oikeat haut: Porvoo, Espoo, Inari, Borgå, Nikunmäki Espoossa ja Kaivopuiston nimipiste Helsingissä. Pyyntöparametrit tarkistetaan testisäikeessä, mukaan lukien `size=100` ja `lang=fi`.
- FMI:n oikeat haut: Porvoon kuntapisteelle **Porvoo Harabacka, 3,3 km**, Inarin vertailupisteelle **Inari Kaamanen, 28,4 km**. Havainnot olivat tuoreita, ennustekoordinaatit säilyivät ja molemmat tiedot palautuivat välimuistista. Myös aiemmat FMI/Open-Meteo/Elering-verkkotestit ajettiin.
- Windowsin Robolectric ajaa Androidin AtomicFilen Java-isäntäjärjestelmän päällä. Windowsin `File.renameTo` ei korvaa olemassa olevaa kohdetta Android/Linuxin tavoin. Kahden live-paikan ensimmäiset välimuistikierrokset testattiin siksi erillisissä testihakemistoissa. Tämä ei ole puhelimella todennettu välimuistivika.
- Compose-testi käyttää oikeita tekstinsyöttö-, tähti-, paikanvalinta- ja paluupainikkeita. Kuvat `app/build/screenshots/place-search.png` ja `selected-place.png` tarkistettiin. Kuvat sisältävät testidataa.
- Release-allekirjoitus varmennettiin ja sen SHA-256 vastaa aiempaa julkista release-varmennetta: `C70BB7BE13920737478FA81CDD8212BEBEDDC8AC8B6326C7964B6F6EB4FF965E`.

Valmis paikallinen paketti: `Saa-Sahko-0.2.2-release.apk`. APK:n SHA-256: `D4B77C62928C96998D1D8633C66222CD084079D094A1CC50C3154DA70464DCDC`. Aiemmat 0.2.0- ja 0.2.1-APK:t säilyivät muuttumattomina.

Tässä työssä ei käytetty puhelimia tai Pebbleä, asennettu APK:ta eikä tehty GitHub-releasea. Käyttäjä antoi myöhemmin luvan commitointiin ja GitHub-pushiin; alkuperäinen toiminnallinen commit on `2073661` ja sen dokumentaatio `7cd2461`. Puhelimien viimeksi erikseen varmennettu versio on 0.2.1; tämän toteutuksen kahden puhelimen käytös ja pitkä tausta-ajastus on vielä kokeiltava laitteilla.

## Aiemman 15 kohdan katselmoinnin suhde muutokseen

Sijaintiin liittyvät kohdat 2–8 ja testisäikeen kohta 15 käsiteltiin tässä toteutuksessa. Kuntanimien tarkka Geocoder/MML-merkkijonoliitos poistui ensisijaisesta nimeämisestä. Paikan JSON-muunnos jaettiin (11), ja MML:n HTTP-client alustetaan IO-säikeellä (osa 10). HTTP-siltoja ei yhdistetty.

Alkuperäinen 10 sekunnin ilmoitustestin coroutine-ajastin (1), sen nimeäminen/toisto (14) ja aiemmin hyväksytty APK:hon sisältyvä MML-avain (9) eivät muutu tässä työssä. Testipainike ei siksi todista ajastuksen toimivuutta CPU:n ollessa keskeytettynä. Avainta ei tulostettu, vaihdettu tai siirretty Gitiin; julkisen jakelun avaineristys jää erilliseksi päätökseksi. Kanavien rekisteröinnistä ei vahvistettu toiminnallista vikaa (12); `setLocalOnly(false)` ja PRIVATE ovat oletuksia, eivät itsenäinen Pebble-korjaus (13).

## Lähteet

- [MML geokoodauspalvelu](https://www.maanmittauslaitos.fi/kartat-ja-paikkatieto/aineistot-ja-rajapinnat/paikkatietojen-rajapintapalvelut/geokoodauspalvelu), geographic-names / paikannimirekisteri, Maanmittauslaitos 2026, CC BY 4.0. Testin `mml-search-porvoo.json` on lyhennetty julkisen nimiaineiston vastaus 17.9.2026.
- [FMI latauspalvelun pikaohje](https://www.ilmatieteenlaitos.fi/latauspalvelun-pikaohje) ja [FMI aikasarjat](https://en.ilmatieteenlaitos.fi/open-data-manual-time-series-data).
- [Android WorkManagerin ajoitus](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
- [Android AtomicFile](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/util/AtomicFile.java).
