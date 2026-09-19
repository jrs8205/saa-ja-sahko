# Expressive-käyttöliittymän toinen katselmointi — 19.9.2026

katselmoinnin toimittaman raportin kaikki 15 kohtaa tarkistettiin nykyisestä koodista. Vahvistetut toimintaviat korjattiin, puuttuvaa testikattavuutta lisättiin ja ylläpidettävyyskohdat siivottiin. Käyttäjä valtuutti korjausten jälkeen testipuhelimen asennuksen sekä commitit ja GitHub-pushin `ui-pehmea-expressive`-haaraan. Sijaintityö ja UI-korjaukset toimitetaan erillisinä committeina. Uusin toimitustilanne: [jatkomuistio](../jatkomuistio).

## Lähtötila ja rajaus

- Haara `ui-pehmea-expressive`, HEAD `d0a66f2`, pohja `main` / `f6615ee`; 12 UI- ja dokumentaatiocommittia.
- Työpuussa oli valmiiksi kahdeksan sijaintityön tiedostoa. Käyttäjä valtuutti tässä istunnossa kaikkien vahvistettujen löydösten paikallisen korjauksen. Tämä kattaa raportin kohdan 3, vaikka vanha `katselmointipyyntö` rajasi sijaintitiedostot ulos UI-työstä.
- Sijaintityön lähdekoodista muutettiin tällä kierroksella vain `DeviceLocation.kt`:n lopputarkistusta. Sen regressio lisättiin `DeviceLocationTest.kt`:hen ja paikannuksen dokumentaatio täsmennettiin. Aiemmat `AppViewModel`-, `DevicePlaceName`- ja muut sijaintitestimuutokset säilytettiin.
- `AppScreen(...)`-rajapinta, sovelluksen versio 0.2.2 / versionCode 6 ja Gradle-riippuvuudet säilyvät. Alkuperäinen korjausvaihe tehtiin paikallisesti; käyttäjän myöhempi pyyntö valtuutti tämän haaran commitit, pushin ja testipuhelimen päivityksen. Main-yhdistämistä tai GitHub-releasea ei tehty.

## Löydöskohtainen tulos

| # | Arvio | Korjaus ja näyttö |
|---|---|---|
| 1 | Vahvistettu, P2 | `CurrentBlock` sovittaa lämpötilan 24–92 sp:n alueella (suurella fontilla enintään 64 sp). Kirjainväli ja rivikorkeus skaalautuvat valitun tekstikoon mukana. Testi toisti alkuperäisen leikkautumisen; korjattu asettelu kattaa molemmat lähteet, −9/−27/−105 astetta, 320 dp ja fonttikertoimet 1 / 1,15 / 1,6 / 2. Tekstiasettelusta tarkistetaan kaikki merkit ja piirretyt reunat. |
| 2 | Vahvistettu, P2 | Otsikko kertoo puuttuvasta alueesta, latauksesta tai puuttuvasta/epävarmasta tiedosta. Pelkkä `0 varoitusta` näytetään vasta kelvollisen, tuoreen ja kokonaisen syötteen perusteella. Vanhassa, osittaisessa tai virheellisessä tilassa mahdollinen määrä on erikseen merkitty tallennetuksi tiedoksi. Testi kattaa latauksen, verkkovirheen, puuttuvan luvan/paikan, vanhan syötteen, osittaisen syötteen ja aidon nollatuloksen. |
| 3 | Vahvistettu, P2, erillinen sijaintityö | Vastaanotettaessa hyväksyttyä sijaintia ei hylätä uudelleen pelkän tarkennusodotuksen vuoksi. Koordinaattien validointi ja 30 sekunnin tuoreusraja säilyvät vastaanotossa. Palautettu `locatedAt` sisältää todellisen iän myös odotuksen ajalta. Uusi testi toisti 29 s vanhan / 800 m tuloksen hylkäyksen kahden sekunnin odotuksen jälkeen ja varmistaa nyt onnistumisen alkuperäisellä aikaleimalla. Raportin ilmaus ”karkealla luvalla aina” on liian ehdoton: yhden vastaavan lähteen tai jo riittävän tarkan tuloksen tapauksessa lisäodotusta ei tule. |
| 4 | Vahvistettu, P2 | Positiivinen pylväs päättyy nollaviivaan ylhäältä ja negatiivinen alkaa siitä alaspäin. Vähimmäiskorkeus on 2 dp; nolla on 1 dp:n merkki viivalla. Pikselitesti toisti 0,3 snt/kWh:n piirtymisen nollaviivan alle ja tarkistaa nyt myös −0,3:n ja nollan. Valinta ja kosketussolun leveys säilyvät. |
| 5 | Vahvistettu, P2 | `ChoiceRow` mittaa omat vaihtoehtonsa ja siirtyy tarvittaessa pystyasetteluun. Myös pitkät vaihtoehdot saavat rivittyä. Samat sisäreunukset ohjaavat mittausta ja piirtämistä; sähkönäkymä käyttää komponentin omaa vähimmäisleveysfunktiota. Testi kattaa 224 dp:n sisältöalueen, `Open-Meteo`-/`60 min`-tekstit, fontit 1,5 / 1,8 / 2 ja vähintään 44 dp:n valittavat kohteet. |
| 6 | Vahvistettu, P2 | Mittarilaatat näyttävät lähdenimet `FMI` ja `Open-Meteo` myös tekstinä. Saavutettavuuskuvauksessa ovat lähde, mittari, arvo ja yksikkö, esimerkiksi `Ilmatieteen laitos: Tuuli 4,0 m/s`. Puuttuva arvo on `Tieto puuttuu`. Semantiikkatesti ja renderöinnit kattavat muutoksen; fyysistä TalkBack-kuuntelua ei tehty. |
| 7 | Vahvistettu, P2 | Kaikille hintakaavion soluille varataan sama kellonaikatekstin korkeus, myös Nyt-pillerin sisäreunukset ja kesä-/talviajan UTC-poikkeama. Testi toisti aiemman korkeuden vaihtelun. Lisätesti kattaa talviaikaan siirtymisen ja fontit 1 / 2; sen paljastama offset-tekstin leikkautuminen korjattiin antamalla tekstille koko solun leveys. |
| 8 | Vahvistettu, P2 | Sadepylväs alkaa yhteisestä nollatasosta. Vähimmäiskorkeus on kiinteä 2 dp, eikä riipu pylvään leveydestä. Nolla-, puoliväli- ja ylärajan apuviivat palautettiin. 800 dp:n testissä 0,1 mm:n pylväs oli ennen 44 px korkea; korjauksen jälkeen se pysyy enintään 4 px:ssa xhdpi-renderöinnissä. Asteikko on testissä 0–20 mm. |
| 9 | Vahvistettu, P2 | `HourColumns` jakaa saman mitatun kellonaikasarakkeen ja välin otsikon ja tuntirivien kesken. Ahtaassa tilassa kellonaika siirtyy omalle riville. Lähdenimi on yhtenäisesti `Open-Meteo`. 320 dp:n testi vertaa otsikon ja kaikkien lähdesarakkeiden x-koordinaatteja fonteilla 1 / 2 sekä kellonaikoja `03.00 (+03:00)`, `03.00 (+02:00)` ja `04.00`. |
| 10 | Vahvistettu, P3 | Pois käytöstä olevan kytkinrivin molemmat tekstit himmenevät 38 %:n peittävyyteen. Rivin ennestään oikea disabled-semantiikka ja toimimaton napautus säilyvät. Testi tarkistaa molempien tekstien värin sekä sen, ettei callback käynnisty. |
| 11 | Vahvistettu testikattavuuspuute, P3 | `AppTheme`-oletuksen `dynamic = true` kautta lisättiin molempien teemojen renderöinnit kaikille kolmelle näkymälle SDK 33:lla ja 35:llä. Teemat verrataan Androidin `dynamicLightColorScheme`-/`dynamicDarkColorScheme`-tuloksiin. Tekstin ja käytettyjen pinta-/merkitysvärien kontrastit tarkistetaan 4,5:1-rajaa vasten. Käytössä ovat Robolectricin Android-järjestelmäresurssit; tämä ei ole puhelimen käyttäjän taustakuvasta tuottaman paletin tai kaikkien mahdollisten palettien varmennus. |
| 12 | Vahvistettu ylläpidettävyysasia, P3 | Valitsimen mittaus on komponentin sisällä. Hintayhteenvedon mittaus ja piirto käyttävät samoja komponentin omia padding- ja pienimmän fontin arvoja. Aurinkorivin laskenta ja asettelu käyttävät samaa välimittaa. Aiemmat kolme mahtumisregressiota säilyvät. |
| 13 | Vahvistettu päällekkäinen laskenta, P3 | `ScreenData.kt` muodostaa päivän hintarivit muistettuna ilman `now`-avainta ja paikalliset varoitukset kerran. `AppScreen` jakaa ne navigaatiolle ja näkyvälle näkymälle; huomisen hintarivit lasketaan erikseen vasta tarvittaessa. Ajan vaihtuessa nykyinen slotti ja varoitusten voimassaolo päivittyvät edelleen. Todellista laitehidastumista tai nopeutumista ei mitattu. |
| 14 | Vahvistettu siivous, P3 | Poistettiin käyttämätön `WeatherScreen.onOpenPlacePicker` ja `ForecastColors.bottom`. `PriceColors.bottom` säilyy käytössä. Kutsu AppScreenistä päivitettiin; sen julkinen allekirjoitus ei muuttunut. |
| 15 | Vahvistettu ylläpidettävyysasia, P3 | Hintojen ja varoitusten yhteiset keltaiset/oranssit/punaiset värit sijaitsevat `MeaningColors.kt`:ssa. Aiempien värien arvot säilyivät. Kaikki merkitysvärien ja sääsymbolien tumma/vaalea-tarkistukset käyttävät samaa `isDarkTheme()`-funktiota. Kontrastitestit ja aiempi navTint-testi kattavat paletit. |

Aiemman raportin 6.4 koski tämän raportin kohtaa 13 ja 6.12 kohtaa 14. Niiden aiemmat siirtopäätökset ovat historiaa; nämä muutokset on nyt tehty.

## Testit ja kuvat

Kymmenen uutta regressiotestiä havaittiin epäonnistuviksi ennen niihin liittyvää korjausta: lämpötila, varoitusten otsikko, sijainnin ikäraja, hintapylvään etumerkki, valitsimet, mittarien saavutettavuus, Nyt-pillerin korkeus, sadepylväs, tuntisarakkeet ja kytkimen himmennys. Uusi talviajan hintalabelitesti paljasti vielä yhden mahtumisvirheen viimeistelyssä. Dynaamisen teeman testit lisäävät kattavuutta, eivät todista aikaisempaa värivirhettä.

Lopullinen `.\validate.ps1` onnistui 19.9.2026: **141 testiä, 135 läpi, 0 epäonnistumista, 0 virhettä ja 6 live-testiä ohitettu**. `assembleDebug` ja `lintDebug` onnistuivat. Lintissä on 0 virhettä ja 4 aiempaa riippuvuuspäivitysvaroitusta. Testejä on 15 enemmän kuin lähtötilan 126 testin sarjassa; SDK 33/35 -tapaukset lasketaan erikseen. Kaikki testiajot tehtiin yksi kerrallaan ja niiden valmistuminen odotettiin.

Varmennus koskee toimitettavaa lähdepuuta, joka sisältää myös aiemmat sijaintimuutokset. Ennen asennusta ja committeja `.\validate.ps1` varmisti samojen lähteiden portit uudelleen; käännös- ja testitehtävät olivat ajan tasalla. Puhdasta erillistä GitHub-checkoutia ei testattu. `git diff --check` ei ilmoittanut whitespace-virheitä. Käyttöliittymän julkinen `AppScreen`-allekirjoitus ja vanhat testitagit säilyivät; lähdekoodeihin ei lisätty liukuvärejä, `BorderStroke`-reunuksia tai uusia riippuvuuksia.

Kuvat ovat testidataa ja syntyvät kansioon `app/build/screenshots/`. Esimerkkejä:

- `review-temperature-320-*.png`: pakkaslämpötilat eri fonttiskaaloilla.
- `review-choice-320-2.0.png`: valitsimet suurella fontilla.
- `review-signed-price-bars.png` ja `review-tablet-rain.png`: pylväiden suunta ja sadeasteikko.
- `review-hour-columns-dst-320-2.0.png`: talviajan molemmat 03.00-rivit.
- `review-dynamic-{33,35}-{WEATHER,PRICES,WARNINGS}-{light,dark}.png`: tuotannon dynaamisen teemapolun 12 renderöintiä.

## Varmennuksen rajat

Robolectric testaa Android-/Compose-koodia paikallisesti. Käyttäjän puhelimien nykyistä taustakuvapalettia, fyysistä TalkBackia, fonttien laiterenderöintiä, eleohjauksen välyksiä tai Wi-Fi-/mobiilipaikannuksen kestoa ei testattu kattavasti laitteella. Käyttäjä kumosi aiemman päivityskiellon testipuhelimen osalta: uusi debug-APK asennettiin 19.9. klo 08.36.15 `install --no-incremental -r` -komennolla. Allekirjoituksen yhteensopivuus, asennetun APK:n tiiviste ja käynnistys (`Status: ok`) varmennettiin. AppId, CE/DE-inodet ja ensimmäinen asennusaika säilyivät. Sovellusta ei poistettu eikä dataa tyhjennetty. Vanhoja toimitus-APK:ita ei korvattu; uusi tiedosto on `Saa-Sahko-0.2.2-expressive-2026-09-19-debug.apk`. testipuhelimeen ei koskettu.

API-tukena käytettiin Androidin [TextAutoSize-viitettä](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/TextAutoSize) ja [Material 3:n dynaamisen teeman ohjetta](https://developer.android.com/codelabs/jetpack-compose-theming). Todisteet korjausten toiminnasta ovat tämän työpuun testit ja renderöinnit.
