# Sää & Sähkö 0.3.1

Korjausjulkaisu 9.10.2026: valittu paikka näkyy sääruudun otsikossa. Versio **0.3.1**, versionCode **8**, paketti `fi.omasaasahko`, muuten kuten 0.3.0.

- Kun haettu tai suosikkipaikka on valittuna, "VALITTU PAIKKA" -otsikon alla näkyy paikan nimi (suosikilla tähti, esim. "★ Porvoo") sekä laji ja lähde, esimerkiksi "Kunta · Maanmittauslaitos". Aiemmin nimi puuttui ja ainoa teksti oli paluunappi "Nykyinen sijainti", joka luki kuin paikan nimi. Nappi on nyt "Palaa nykyiseen sijaintiin". Sää haettiin jo ennenkin valitun paikan mukaan.
- Release rakennettu `build-release.ps1`-skriptillä: R8, APK Signature Scheme v3, sama julkaisuvarmenne `C7:0B:B7:BE:…:FF:96:5E`. Symbolikartta talletettu varmuuskopiokansioon `mapping-0.3.1-8/`.
- Tiedosto projektin juuressa: `Saa-Sahko-0.3.1-release.apk`, 3,2 MB, SHA-256 `6520DC530BE62CD75E7159AC03010A98F3242DC14970191A92E304825B176D02`.
- [GitHub-julkaisu v0.3.1](https://github.com/jrs8205/saa-ja-sahko/releases/tag/v0.3.1) sisältää APK:n, julkisen varmenteen ja RELEASE-INFO.txt-tiedoston. Puhelinasennukset tehdään käsin myöhemmin.

---

# Sää & Sähkö 0.3.0

Julkaisu 5.10.2026: Pehmeä Expressive -käyttöliittymä, WCAG AAA -kontrastit, isommat sääsymbolit ja lämpötilat yhden desimaalin tarkkuudella. Versio **0.3.0**, versionCode **7**, paketti `fi.omasaasahko`, Android 13+ (API 33), kohde Android 17 (API 37).

- Release rakennetaan R8:lla (`isMinifyEnabled` ja `isShrinkResources`), säännöt tiedostossa `app/proguard-rules.pro`. Koko pieneni 26,1 MB:stä 3,2 MB:iin. Symbolikartta syntyy polkuun `app/build/outputs/mapping/release/mapping.txt`; säilytä se jokaisesta julkaistusta koonnista pinotulosteiden lukemista varten.
- APK allekirjoitetaan pysyvällä julkaisuavaimella APK Signature Scheme v3:lla (minSdk 33 ei tarvitse v1/v2-allekirjoituksia). `apksigner verify --print-certs` vahvisti varmenteen SHA-256:n `C7:0B:B7:BE:…:FF:96:5E`, joka on sama kuin `docs/release-certificate.pem`-tiedostossa ja aiemmissa julkaisuissa.
- [GitHub-julkaisu v0.3.0](https://github.com/jrs8205/saa-ja-sahko/releases/tag/v0.3.0) sisältää APK:n, julkisen varmenteen ja RELEASE-INFO.txt-tiedoston.
- Tiedosto projektin juuressa: `Saa-Sahko-0.3.0-release.apk`, SHA-256 `692B574DC0EAE010A22FD828365FFC8952F653FD79955F8E178720E3247A14B3`. Ei debuggable-lippua.
- Asennettu 5.10.2026 ADB:llä kolmeen testipuhelimeen: kahteen päivityksenä tiedot säilyttäen, yhteen aiemman debug-asennuksen tilalle (sovellusdata nollautui, koska debug- ja release-allekirjoitus eroavat). Asennettujen pakettien SHA-256 ja varmenne täsmäsivät. Sää-, Sähkö- ja Varoitukset-näkymät toimivat oikealla datalla, ei kaatumisia; WorkManagerin taustatyöt rekisteröityivät.

---

# Sää & Sähkö 0.2.0

Henkilökohtainen Android-sovellus: Ilmatieteen laitoksen ja Open-Meteon sää, Suomen pörssisähkö sekä sijaintiin kohdistuvat FMI-varoitukset. Vaalea ja tumma teema seuraavat puhelimen asetusta.

## Tässä julkaisussa

- Sääkorttien värit ja yhtenäinen korkeus, nykyinen kellonaika, FMI:n aurinkoajat sekä lähdenimet myös jokaisessa viikkokortissa.
- Varttihinnat ja tuntikeskiarvot, muistettava ALV 25,5 % -kytkin ja aina kolme desimaalia. Hintavärit, 44 dp:n pylväät, sivulle vieritettävä kaavio ja kiinteä hinta-asteikko.
- Oma Varoitukset-välilehti: kaikki FMI:n julkaisemat tulevat varoitukset ja valittavat päivät. Alue ratkaistaan virallisten CAP-polygonien avulla. Päiväkortit ovat vähintään 112 × 80 dp ja kasvavat fonttikoon mukana; päivä ja päivämäärä näkyvät omilla riveillään.
- Valinnaiset keltaiset, oranssit ja punaiset varoitusilmoitukset. Tarkistus 15/30/60 minuutin välein, oletus 30 minuuttia, viimeksi sovelluksessa haettuun sijaintiin.
- Sähkösivun **Ilmoita huomisen hinnat** -kytkin: ilmoitus kerran, kun koko seuraavan Suomen vuorokauden hinnat ovat saatavilla. Mukana keskihinta ja halvin/kallein tunti, kolme desimaalia sekä valittu ALV.
- Oma adaptiivinen sovelluskuvake ja Androidin teemakuvake.

Ilmoitukset otetaan käyttöön sivujen kytkimistä ja Androidin ilmoitusluvalla. Android voi viivästyttää taustatarkistuksia; kyse on puhelimessa tehtävästä hausta ja paikallisesta ilmoituksesta. Taustapaikannusta ei käytetä. Matkalla avaa sovellus, jotta säävaroitusten sijainti päivittyy.

## APK ja asennus

- Tiedosto projektin juuressa ja GitHub-julkaisun liitteenä: `Saa-Sahko-0.2.0-release.apk`.
- [GitHub-julkaisu v0.2.0](https://github.com/jrs8205/saa-ja-sahko/releases/tag/v0.2.0). Ladatun APK:n voi asentaa puhelimeen.
- Android 13 tai uudempi; kohdealusta Android 17. Versio 0.2.0, versionCode 4.
- APK-tiedoston SHA-256: `1F99D10EDCA9C40911E984B47CF4063955120405ED97769E5DCEC6F778991098`.

Release asennettiin testipuhelimeen, ja toinen testipuhelin päivitettiin debug-versioon aiemmat sovellustiedot säilyttäen. Asennettujen pakettien tiivisteet tarkistettiin. Debug ja release käyttävät eri allekirjoituksia, joten release ei päivity suoraan debug-asennuksen päälle samalla pakettinimellä. Uuteen puhelimeen käytetään tämän julkaisun release-APK:ta.

## Google Android Developer Console

Pakettinimi:

```text
fi.omasaasahko
```

Allekirjoitusvarmenteen SHA-256-sormenjälki:

```text
C7:0B:B7:BE:13:92:07:37:47:8F:A8:1C:DD:82:12:BE:BE:DD:C8:AC:8B:63:26:C7:96:4B:6F:6E:B4:FF:96:5E
```

Googlelle annetaan **varmenteen** sormenjälki, ei ylempänä olevaa APK-tiedoston tiivistettä. Julkinen varmenne on `docs/release-certificate.pem`. Yksityinen allekirjoitusavain ei kuulu Googleen tai GitHubiin.

**Käyttäjä vahvisti 16.9.2026 rekisteröinnin onnistuneen Google Android Developer Consolessa.** Rekisteröinti ei ole enää avoin tehtävä. Konsoliin ei kirjauduttu avustajan toimesta; tieto perustuu käyttäjän vahvistukseen. Sovellusta ei julkaistu Play-kauppaan. [Googlen rekisteröintiohje](https://support.google.com/android-developer-console/answer/16640821?hl=en) on viitteenä mahdollisia myöhempiä tarpeita varten.

## Avaimen säilyttäminen ja tulevat päivitykset

Ota turvalliseen varmuuskopioon molemmat paikalliset tiedostot:

- `.signing/saa-sahko-release.p12`
- `release-signing.properties`

Ne sisältävät yksityisen avaimen ja sen käyttöön tarvittavat salasanat. Molemmat on rajattu paikallisilla käyttöoikeuksilla ja ohitettu Gitissä. Sama avain tarvitaan myöhempiin päivityksiin; sitä ei pidä luoda uudelleen. Julkinen varmenne ei yksin riitä allekirjoittamiseen.

`build-release.ps1` rakentaa ja tarkistaa allekirjoitetun APK:n sekä kopioi sen juureen. `prepare-release-key.ps1` on vain ensimmäisen avaimen luontia varten ja kieltäytyy korvaamasta olemassa olevaa avainta.

## Varmennus 16.9.2026

- Debug- ja release-käännökset onnistuivat. **45 testiä läpi, ei ohitettuja tai epäonnistuneita testejä.** Debug- ja release-lint: 0 virhettä, kummassakin 4 riippuvuuksien/Gradlen päivitysilmoitusta.
- Viimeisin verkkotesti klo 21.18 Suomen aikaa: kaikki 192 FI-varttia täsmäsivät Eleringin ja Nord Poolin välillä; 480 vartti-/tuntinäyttöä tarkistettiin ALV päällä ja pois.
- FMI:n ja Open-Meteon koodit, yksiköt ja aikaikkunat tarkistettiin virallisista ohjeista ja oikeista vastauksista. CAP-aluekohdistus, muutokset/perumiset ja tulevat varoitukset testattiin.
- Molemmat teemat, iso fontti, kaavion vieritys ja valinta, viikon lähdenimet, varoitusten päivät sekä ilmoitusasetukset tarkistettiin paikallisilla käyttöliittymätesteillä ja kuvista.
- Molempien puhelimien asennus ja APK-tiivisteet varmennettiin. Lopullisen version kaikkia toimintoja tai tausta-ajastusten viivettä ei ole fyysisillä laitteilla käyty läpi; pitkäaikaista akkukulutusta ei ole mitattu.

Tarkempi näyttö: [hintavarmennus](docs/PRICE-VERIFICATION.md), [sää- ja varoitusvarmennus](docs/WEATHER-VERIFICATION.md). Ajonaikainen hintalähde on Elering; Nord Pool -vertailu on erikseen ajettava testi. Yksi tarkistus ei takaa ulkoisten palveluiden tulevien vastausten virheettömyyttä.
