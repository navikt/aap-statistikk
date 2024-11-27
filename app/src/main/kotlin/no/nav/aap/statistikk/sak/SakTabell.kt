package no.nav.aap.statistikk.sak

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.bigquery.BQTable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class SakTabell : BQTable<BQBehandling> {
    companion object {
        const val TABLE_NAME = "sak"
    }

    override val tableName = TABLE_NAME

    override val version: Int = 0
    override val schema: Schema
        get() {
            val fagsystemNavn = Field.of("fagsystemNavn", StandardSQLTypeName.STRING)
            val saksnummmer = Field.of("saksnummer", StandardSQLTypeName.STRING)
            val behandlingUuid = Field.of("behandlingUuid", StandardSQLTypeName.STRING)
            val relatertBehandlingUUid =
                Field.newBuilder("relatertBehandlingUUid", StandardSQLTypeName.STRING)
                    .setMode(Field.Mode.NULLABLE).build()
            val relatertFagsystem =
                Field.newBuilder("relatertFagsystem", StandardSQLTypeName.STRING)
                    .setMode(Field.Mode.NULLABLE)
                    .setDescription("Hvis feltet relatertBehandlingId er populert, er denne populert med 'KELVIN'.")
                    .build()
            val behandlingType = Field.of("behandlingType", StandardSQLTypeName.STRING)
            val ferdigbehandletTid =
                Field.newBuilder("ferdigbehandletTid", StandardSQLTypeName.DATETIME)
                    .setMode(Field.Mode.NULLABLE).build()
            val aktorId = Field.of("aktorId", StandardSQLTypeName.STRING)
            val tekniskTid =
                Field.newBuilder("tekniskTid", StandardSQLTypeName.TIMESTAMP)
                    .setDescription("Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.")
                    .build()
            val mottattTid = Field.newBuilder("mottattTid", StandardSQLTypeName.DATETIME)
                .setDescription("Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid.")
                .build()
            val endretTid = Field.newBuilder("endretTid", StandardSQLTypeName.TIMESTAMP)
                .setDescription("Tidspunkt for siste endring på behandlingen. Ved første melding vil denne være lik registrertTid.")
                .build()
            val registrertTid = Field.newBuilder("registrertTid", StandardSQLTypeName.DATETIME)
                .setDescription("Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.")
                .build()
            val versjon = Field.of("versjon", StandardSQLTypeName.STRING)
            val avsender = Field.of("avsender", StandardSQLTypeName.STRING)
            val sekvensNummer = Field.of("sekvensnummer", StandardSQLTypeName.INT64)
            val opprettetAv = Field.newBuilder("opprettetAv", StandardSQLTypeName.STRING)
                .setDescription("Vil alltid være systemet Kelvin.").build()
            val saksbehandler = Field.newBuilder("saksbehandler", StandardSQLTypeName.STRING)
                .setMode(Field.Mode.NULLABLE)
                .setDescription(
                    """
                    [Geo-lokaliserende: oppgis som -5 hvis noen personer tilknyttet behandlingen er kode 6] Nav-Ident til saksbehandler som jobber med behandlingen. Vil være
                    null om ingen saksbehandlere har vært innom saken.
                    """.trimIndent()
                )
                .build()
            val vedtakTid = Field.newBuilder("vedtakTid", StandardSQLTypeName.DATETIME)
                .setDescription("Tidspunktet for når vedtaket ble fattet - hvis saken ble vedtatt.")
                .setMode(Field.Mode.NULLABLE).build()

            val søknadsFormat =
                Field.newBuilder("soknadsformat", StandardSQLTypeName.STRING)
                    .setDescription("Angir om søknaden har kommet inn via digital søknadsdialog eller om den er scannet (sendt per post). Mulige verdier: DIGITAL/PAPIR.")
                    .build()

            val behandlingMetode =
                Field.newBuilder("behandlingMetode", StandardSQLTypeName.STRING).setDescription(
                    """
                Kode som angir om behandlingen er manuell eller automatisk. Det er den akkumulerte verdien som er interessant, det vil si at
                hvis behandlingen har hatt en manuell hendelse bør fremtidige rader ha verdien MANUELL (selv om enkelthendelsene kan ha vært automatiske).
            """.trimIndent()
                ).build()

            val ansvarligBeslutter =
                Field.newBuilder("ansvarligBeslutter", StandardSQLTypeName.STRING).setDescription(
                    """
                    Ved krav om totrinnskontroll skal dette feltet innholde Nav-Ident til ansvarlig beslutter.
                """.trimIndent()
                ).build()

            // Tomme felter som skal med i spec, men som vi ikke leverer på/ikke har implementert
            val utbetaltTid =
                Field.newBuilder("utbetaltTid", StandardSQLTypeName.DATE)
                    .setDescription("Tidspunkt for første utbetaling av ytelse.")
                    .setMode(Field.Mode.NULLABLE).build()

            val forventOppstartTid =
                Field.newBuilder("forventetOppstartTid", StandardSQLTypeName.DATE)
                    .setDescription("Hvis systemet eller bruker har et forhold til når ytelsen normalt skal utbetales (planlagt uttak, ønsket oppstart etc).")
                    .build()

            val sakYtelse =
                Field.newBuilder("sakYtelse", StandardSQLTypeName.STRING)
                    .setDescription("Kode som angir hvilken ytelse/stønad behandlingen gjelder.")
                    .build()

            val sakUtland =
                Field.newBuilder("sakUtland", StandardSQLTypeName.STRING).setDescription(
                    "Kode som angir hvor vidt saken er for utland eller nasjonal å anses. Se begrepskatalogen: https://jira.adeo.no/browse/BEGREP-1611#"
                ).build()

            val behandlingStatus = Field.of("behandlingStatus", StandardSQLTypeName.STRING)

            val behandlingResultat =
                Field.newBuilder("behandlingResultat", StandardSQLTypeName.STRING).setDescription(
                    """
                Kode som angir resultatet på behandling - typisk: avbrutt, innvilget, delvis innvilget, henlagt av tekniske hensyn, etc.

                For behandlingstype klage: Angi behandlingsresultat fra vedtaksinstans, selv om det ikke er et endelig resultat på behandlingen - typisk: Fastholdt, omgjort.

                Resultat er forventet hvis status for eksempel Avsluttet (enten produsert eller avbrutt)
                  """
                ).build()

            val resultatBegrunnelse =
                Field.newBuilder("resultatBegrunnelse", StandardSQLTypeName.STRING).setDescription(
                    """
                Kode som angir en begrunnelse til resultat ved avslag- typisk: vilkårsprøvingen feilet, dublett, teknisk avvik, etc.

                For behandlingstype klage: Angi begrunnelse ved resultat omgjøring - typisk: feil lovanvendelse, endret faktum, saksbehandlingsfeil, etc.
            """.trimIndent()
                ).build()

            val ansvarligEnhet =
                Field.newBuilder("ansvarligEnhet", StandardSQLTypeName.STRING).setDescription(
                    """
                    Organisasjons-Id til NAV-enhet som har ansvaret for behandlingen (hvis nasjonal kø benyttes skal denne Org-IDen benyttes her).

                    For behandlingstype klage: Viktig ved oversendelse til KA-instans, hvor vi forventer en 42-enhet
                """.trimIndent()
                ).build()

            val tilbakekrevbeløp =
                Field.newBuilder("tilbakekrevBelop", StandardSQLTypeName.FLOAT64).setDescription(
                    """
                Gjelder kun behandlingstype tilbakreving. Beløp til innkreving.
            """.trimIndent()
                ).build()

            val funksjonellPeriodeFom =
                Field.newBuilder("funksjonellPeriodeFom", StandardSQLTypeName.DATETIME)
                    .setDescription(
                        "Gjelder kun behandlingstype tilbakreving. Tidspunkt som representerer start på periode som feilutbetalingen gjelder"
                    ).build()
            val funksjonellPeriodeTom =
                Field.newBuilder("funksjonellPeriodeTom", StandardSQLTypeName.DATETIME)
                    .setDescription("Gjelder kun behandlingstype tilbakreving. Tidspunkt som representerer slutten av perioden som feilutbetalingen gjelder")
                    .build()

            val behandlingId = Field.newBuilder("behandlingId", StandardSQLTypeName.STRING)
                .setDescription("Alltid null. Vi leverer UUID i stedet.")
                .setMode(Field.Mode.NULLABLE).build()

            val sakId = Field.newBuilder("sakId", StandardSQLTypeName.STRING)
                .setDescription("Alltid null. Vi leverer saksnummer i stedet.")
                .setMode(Field.Mode.NULLABLE).build()



            return Schema.of(
                fagsystemNavn,
                sekvensNummer,
                saksnummmer,
                behandlingUuid,
                relatertBehandlingUUid,
                relatertFagsystem,
                behandlingType,
                ferdigbehandletTid,
                endretTid,
                aktorId,
                tekniskTid,
                mottattTid,
                registrertTid,
                versjon,
                avsender,
                opprettetAv,
                saksbehandler,
                vedtakTid,
                søknadsFormat,
                behandlingMetode,

                // Ikke implementert ennå
                behandlingStatus,
                utbetaltTid,
                forventOppstartTid,
                sakYtelse,
                sakUtland,
                behandlingResultat,
                resultatBegrunnelse,
                ansvarligBeslutter,
                ansvarligEnhet,
                tilbakekrevbeløp,
                funksjonellPeriodeFom,
                funksjonellPeriodeTom,

                // Vil alltid være null
                behandlingId,
                sakId,
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): BQBehandling {
        val fagsystemNavn = fieldValueList.get("fagsystemNavn").stringValue
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlingUuid = fieldValueList.get("behandlingUuid").stringValue
        val relatertBehandlingUUid =
            if (!fieldValueList.get("relatertBehandlingUUid").isNull) fieldValueList.get("relatertBehandlingUUid").stringValue else null
        val relatertFagsystem =
            if (!fieldValueList.get("relatertFagsystem").isNull) fieldValueList.get("relatertFagsystem").stringValue else null
        val ferdigbehandletTid =
            if (!fieldValueList.get("ferdigbehandletTid").isNull) fieldValueList.get("ferdigbehandletTid").stringValue else null
        val tekniskTid = fieldValueList.get("tekniskTid").timestampValue
        val mottattTid = fieldValueList.get("mottattTid").stringValue
        val endretTid = fieldValueList.get("endretTid").timestampValue
        val registrertTid = fieldValueList.get("registrertTid").stringValue
        val behandlingType = fieldValueList.get("behandlingType").stringValue
        val versjon = fieldValueList.get("versjon").stringValue
        val avsender = fieldValueList.get("avsender").stringValue
        val sekvensNummer = fieldValueList.get("sekvensnummer").longValue
        val aktorId = fieldValueList.get("aktorId").stringValue
        val ansvarligBeslutter =
            if (!fieldValueList.get("ansvarligBeslutter").isNull) fieldValueList.get("ansvarligBeslutter").stringValue else null
        val opprettetAv = fieldValueList.get("opprettetAv").stringValue
        val saksbehandler = fieldValueList.get("saksbehandler").stringValue
        val søknadsFormat = fieldValueList.get("soknadsformat").stringValue
        val vedtakTid =
            if (!fieldValueList.get("vedtakTid").isNull) fieldValueList.get("vedtakTid").stringValue else null

        val behandlingMetode = fieldValueList.get("behandlingMetode").stringValue

        return BQBehandling(
            fagsystemNavn = fagsystemNavn,
            saksnummer = saksnummer,
            behandlingUUID = behandlingUuid,
            relatertBehandlingUUID = relatertBehandlingUUid,
            relatertFagsystem = relatertFagsystem,
            tekniskTid = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(
                    tekniskTid / (1000 * 1000),
                    tekniskTid.rem(1000 * 1000) * 1000.toLong()
                ),
                ZoneId.of("Z")
            ),
            behandlingType = behandlingType,
            avsender = avsender,
            verson = versjon,
            sekvensNummer = sekvensNummer,
            aktorId = aktorId,
            mottattTid = LocalDateTime.parse(mottattTid),
            registrertTid = LocalDateTime.parse(registrertTid),
            ferdigbehandletTid = ferdigbehandletTid?.let { LocalDateTime.parse(it) },
            endretTid = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(
                    endretTid / (1000 * 1000),
                    endretTid.rem(1000 * 1000) * 1000.toLong()
                ),
                ZoneId.of("Z")
            ),
            opprettetAv = opprettetAv,
            saksbehandler = saksbehandler,
            vedtakTid = vedtakTid?.let { LocalDateTime.parse(it) },
            søknadsFormat = SøknadsFormat.valueOf(søknadsFormat),
            behandlingMetode = if (behandlingMetode == "MANUELL") BehandlingMetode.MANUELL else BehandlingMetode.AUTOMATISK,
            ansvarligBeslutter = ansvarligBeslutter
        )
    }

    override fun toRow(value: BQBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "fagsystemNavn" to value.fagsystemNavn,
                "sekvensnummer" to value.sekvensNummer,
                "saksnummer" to value.saksnummer,
                "behandlingUuid" to value.behandlingUUID,
                "relatertBehandlingUUid" to value.relatertBehandlingUUID,
                "relatertFagsystem" to value.relatertFagsystem,
                "behandlingType" to value.behandlingType,
                "aktorId" to value.aktorId,
                "tekniskTid" to value.tekniskTid.toInstant(ZoneOffset.UTC).toEpochMilli() / 1000.0,
                "mottattTid" to value.mottattTid.truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "endretTid" to value.endretTid.truncatedTo(ChronoUnit.MILLIS)
                    .toInstant(ZoneOffset.UTC).toEpochMilli() / 1000.0,
                "registrertTid" to value.registrertTid.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "avsender" to value.avsender,
                "versjon" to value.verson,
                "ferdigbehandletTid" to value.ferdigbehandletTid?.truncatedTo(ChronoUnit.SECONDS)
                    ?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "opprettetAv" to value.opprettetAv,
                "saksbehandler" to value.saksbehandler,
                "vedtakTid" to value.vedtakTid?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "soknadsformat" to value.søknadsFormat.toString(),
                "behandlingMetode" to value.behandlingMetode.toString(),
                "ansvarligBeslutter" to value.ansvarligBeslutter,
            )
        )
    }
}