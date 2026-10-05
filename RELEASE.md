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
- [GitHub-julkaisu v0.2.0](https://github.com/jrs8205/saa-porssi-sovellus/releases/tag/v0.2.0). Ladatun APK:n voi asentaa puhelimeen.
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
