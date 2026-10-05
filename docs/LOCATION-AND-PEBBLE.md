# Paikannimet ja Pebble Time 2 — tarkistus 17.9.2026

Tämä dokumentti kuvaa aiempaa 0.2.1-istuntoa. Uudemmat paikkahaku-, havaintoasema- ja ilmoitusmuutokset sekä varmennukset ovat [0.2.2:n dokumentissa](LOCATION-0.2.2.md).

Jatkoversio 0.2.1 asennettiin 17.9.2026 kahteen testipuhelimeen (release ja debug) tietoja säilyttäen. Ei vielä GitHub-julkaisua. Allekirjoitettu ja tarkistettu APK: `Saa-Sahko-0.2.1-release.apk`, SHA-256 `30BC4CA7900CC6FF84D666456874638A7F759FE411A4ECC63196B602F0F336CB`. Julkaistun 0.2.0-version APK säilyy erillisenä.

Molempien laitteiden asennetun APK:n tiiviste vastasi paikallista pakettia ja alkuperäiset asennusajat sekä CE/DE-tietohakemistojen tunnisteet säilyivät. Molempien oikea sijaintihaku näytti kunnan ja MML:n lähimmän nimipisteen. Android ei palauttanut kaupunginosaa. **Käyttäjä vahvisti sekä Säävaroitusten testi- että Sähköhintojen testi -ilmoituksen saapuneen Pebble Time 2:een puhelimen näytön ollessa lukittu.** Ensimmäinen yritys uusittiin käyttäjän ilmoittaman DND-tilan takia.

## Paikannimet

Aiemman oman Arkikeskus-projektin mobiili- (`MmlGeocodingClient`) ja tablettitoteutus (`MmlReverseGeocoder`) tarkistettiin lukemalla. Ne käyttävät Maanmittauslaitoksen Geocoding v2 -palvelua. Niiden koko nimihakua ei kopioitu: pelkkä `geographic-names`-lähde ei tarkoita kaupunginosaa, ja käänteishaun nimet ovat pisteitä. Muut projektit säilyivät muuttamattomina.

MML:n suora käänteishaku Kaivopuiston nimipisteelle palautti 5.10.2026 kohteen **P_10342656**, nimen **Kaivopuisto** (ruotsiksi Brunnsparken), kunnan **Helsinki**, paikkatyypin **Kaupunkialueen tai taajaman osa** (3020105) ja nimipisteen 60.1579733, 24.9589938. Tämä on julkisen puiston piste, ei käyttäjän puhelimesta luettu sijainti. Testiaineisto on rajattu neljään lähimpään kohteeseen. Ajantasainen palvelukysely ei tarkoita, että itse nimi olisi muutettu samana päivänä.

Kaupunginosa ja kunta säilyvät Androidin geokoodauksesta. MML-nimi näytetään niiden alla erikseen tekstillä **Lähin paikannimi · Maanmittauslaitos**. Esimerkiksi Androidin palauttaman `Tikkurila, Vantaa` -otsikon alla voi näkyä lähin MML:n nimipiste. MML-pisteellä ei päätellä, että kaikki sen lähistöllä sijaitsevat koordinaatit kuuluvat samaan kaupunginosaan. Android ei aina palauta kaupunginosaa; silloin otsikossa voi olla vain kunta.

- MML-haussa `sources=geographic-names`, `lang=fi`, `size=100`, `boundary.circle.radius=750`. MML:n OpenAPI-kuvauksen mukaan hakusäde on metrejä; vastauksen `distance` on oletuskoordinaatistossa kilometrejä. Toteutus laskee etäisyyden itse WGS84-pisteistä ja varmistaa 750 m rajan.
- Mukaan otetaan suomenkieliset maasto-, vesistö- ja asutusnimet, pois hallinnolliset keskukset, liikepaikat, rakennukset ja kadut. Lähin kelvollinen nimipiste valitaan etäisyyden mukaan. Toisen kunnan nimeä ei yhdistetä Androidin palauttamaan kuntaan.
- Tarkka sijaintilupa ja enintään 200 m paikannusepävarmuus vaaditaan tarkempaan nimeen. Likimääräisellä luvalla sää jatkaa toimintaansa ilman yksityiskohtaista nimipistettä.
- Geokoodaus tehdään etualan sijaintihaussa. Androidin nimihaku ja MML-haku kulkevat rinnakkain; MML-aikaraja on 6 s. Peruutus keskeyttää verkkopyynnön. Virhe, puuttuva avain tai kelvoton vastaus jättää MML-nimen pois. Ennusteiden koordinaatteja ei siirretä nimipisteeseen.
- Tarkempi nimi säilyy ennustevälimuistissa ja varoitusten sijaintitallennuksessa; vanha välimuisti ilman kenttää kelpaa edelleen.
- Käyttäjän 17.9.2026 luvalla Arkikeskuksen MML-avain kopioitiin vain tämän projektin Gitin ohittamaan `local.properties`-tiedostoon. Avainta ei tulostettu. HTTP käyttää Basic-tunnistautumista otsakkeessa, ei URL:ssa. Avain päätyy APK:hon kuten Arkikeskuksessa; APK:n jakelu on erillinen vaihe.

Lähteet: [MML Geokoodaus v2](https://www.maanmittauslaitos.fi/kartat-ja-paikkatieto/aineistot-ja-rajapinnat/paikkatietojen-rajapintapalvelut/geokoodauspalvelu), [MML OpenAPI](https://avoin-paikkatieto.maanmittauslaitos.fi/geocoding/openapi.json) (vaatii avaimen). Testiaineiston tiedot: Maanmittauslaitos 17.9.2026, [CC BY 4.0](https://www.maanmittauslaitos.fi/en/opendata-licence-cc40).

## Pebble Time 2

Tarkistettiin valmistajan ajantasaiset ohjeet ja julkisen `coredevices/mobileapp`-projektin ilmoituskäsittely. Pebble lukee tavallisen Android-ilmoituksen `EXTRA_TITLE`-otsikon ja ensisijaisesti `EXTRA_BIG_TEXT`-tekstin, toissijaisesti `EXTRA_TEXT`-tekstin. Molemmat sovelluksen kanavat käyttävät näitä kenttiä jo nykyisessä 0.2.0:ssa. Lähdekoodista ei löytynyt näiden kenttien osalta välitystä rikkovaa virhettä.

0.2.1 rekisteröi molemmat pysyvät kanavat sovelluksen käynnistyessä: `weather-warnings` / **Oman alueen säävaroitukset**, `tomorrow-electricity` / **Huomisen sähköhinnat**. Ilmoitukset eivät ole jatkuvia, ryhmäyhteenvetoja tai `localOnly`-ilmoituksia. Kanavien käyttäjäasetuksia ei nollata eikä kanavatunnuksia vaihdeta. Sähkölle lisättiin oma testipainike. Testi käyttää samaa kanavaa ja tekstirakennetta kuin oikea ilmoitus, mutta ei hintatietoja eikä toimituspäivän kertailmoitusmerkintää.

Pebblen `NotificationHandler` voi suodattaa ilmoituksen, kun puhelimen näyttö on auki, jos `alwaysSendNotifications` ei ole käytössä. Siksi molempien kanavien käyttöliittymätestipainikkeissa on 10 sekunnin viive. Tänä aikana voi lukita puhelimen. Toistuvat painallukset eivät lähetä useita testejä; kanavan kytkeminen pois peruuttaa odottavan testin. Testi ei ole pysyvä taustatyö: prosessin kuolema voi peruuttaa sen.

Kokeilu kellolla:

1. Varmista Pebble-puhelinsovelluksessa ilmoitusten lukuoikeus ja kelloyhteys. Salli **Sää & Sähkö** ja kumpikin yllä nimetty kanava. Androidin omien sovellus- ja kanavailmoitusten täytyy olla sallittuina.
2. Sää & Sähkö → Varoitukset → Ilmoitusasetukset → **Testaa ilmoitus 10 s kuluttua**. Lukitse puhelin ja tarkista otsikko **Säävaroitusten testi** sekä koko teksti kellosta.
3. Pörssi-sähkö → Ilmoita huomisen hinnat → **Testaa ilmoitus 10 s kuluttua**. Lukitse puhelin ja tarkista **Sähköhintojen testi**.
4. Jos ilmoitus näkyy mutta kello ei värise, tarkista kellon Quiet Time sekä Sounds + Haptics. Jos testi näkyy vain puhelimessa, tarkista Pebblen sovellus-/kanavasuodatus, näyttö päällä -asetus ja yhteys.

Lopullisessa laitekokeessa näyttö sammutettiin 7,0 sekuntia ensimmäisen 10 s testiajastimen käynnistyksestä. ADB varmisti `Dozing`, lukitusnäytön `showing=true` ja `inputRestricted=true` sekä molemmat sovelluksen aktiiviset testi-ilmoitukset. Käyttäjä vahvisti molemmat otsikot kellossa. Lukituksen avaamista tarvittiin vain testipainikkeiden painamiseen; itse lähetys ja vastaanotto tapahtuivat lukittuna.

Varsinainen FMI-varoitus käyttää samaa toimivaksi todettua `weather-warnings`-kanavaa. Ilmoituskytkin oli käytössä ja tarkistusväli 30 min. Puhelimen JobSchedulerista varmennettiin rekisteröidyt `WarningWorker` ja `PriceAlertWorker`: ne odottivat ajastusrajaa, eikä verkko- tai taustarajoitus estänyt niitä tarkistushetkellä. Uuden oikean FMI-varoituksen tai sähkön hintajulkaisun luonnollista taustasaapumista ja pitkää Doze-jaksoa ei odotettu tässä kokeessa. Näytön lukitus ei estä sovelluksen ilmoituslogiikkaa; Android voi silti viivästyttää taustahakua. Kellon värinästä ei saatu erillistä vahvistusta.

Lähteet: [Pebble BasicNotificationProcessor](https://github.com/coredevices/mobileapp/blob/master/libpebble3/src/androidMain/kotlin/io/rebble/libpebblecommon/notification/processor/BasicNotificationProcessor.kt), [Pebble NotificationHandler](https://github.com/coredevices/mobileapp/blob/master/libpebble3/src/androidMain/kotlin/io/rebble/libpebblecommon/notification/NotificationHandler.kt), [Pebble Time 2 FAQ, päivitetty 19.8.2026](https://help.repebble.com/en/articles/16318214-pebble-watch-faq-tips).

## Paikallinen varmennus

```powershell
$env:SAA_LOCATION_CHECK = '1'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --console=plain
```

57 testiä: 54 läpi, 0 virhettä, 3 erillistä sää-/hintaverkkotestiä ohitettu. MML-verkkotesti suoritettiin ja meni läpi. Lint: 0 virhettä ja neljä aiempaa riippuvuus-/Gradle-päivitysvaroitusta. Compose-kuva tarkistettu: `app/build/screenshots/nearby-place.png` (testisää ja julkinen paikannimi). Testit kattavat nimien valinnan, kuntaerot, virhevastaukset, HTTP-parametrit, nimien tallennuksen, ilmoitusten tekstikentät, estettyjen kanavien säilymisen, 10 sekunnin viiveen/peruutuksen ja oikean hintailmoituksen riippumattomuuden testistä.
