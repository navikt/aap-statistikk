package no.nav.aap.statistikk.sak

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.bigquery.BQTable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/*
* Mangler:
*  - vedtakTid
*  - utbetaltTid
*  - endretTid/funksjonellTid
*  - søknadsformt
*  - forventetOppstartTid
*  - sakYtelse
*  - behandlingStatus
*  - behandlingResultat
*  - resultatBegrunnelse
*  - behandlingMetode (manuell, automatisk, etc)
*  - opprettetAv
*  - saksbehandler
*  - ansvarligbeslutter
*  - ansvarligenhet
*  - tilbakekreveløp (behandlingtype tilbekakreving - har vi dette?)
*  - FunksjonellPeriodeFom - samme
*  - Vilkårsprøving (egen tabell)
 */
class SakTabell : BQTable<BQBehandling> {
    companion object {
        const val TABLE_NAME = "sak"
    }

    override val tableName = TABLE_NAME

    override val version: Int = 0
    override val schema: Schema
        get() {
            val saksnummmer = Field.of("saksnummer", StandardSQLTypeName.STRING)
            val behandlingUuid = Field.of("behandlingUuid", StandardSQLTypeName.STRING)
            val relatertBehandlingUUid =
                Field.newBuilder("relatertBehandlingUUid", StandardSQLTypeName.STRING)
                    .setMode(Field.Mode.NULLABLE).build()
            val behandlingType = Field.of("behandlingType", StandardSQLTypeName.STRING)
            val behandlingStatus = Field.of("behandlingStatus", StandardSQLTypeName.STRING)
            val ferdigbehandletTid =
                Field.newBuilder("ferdigbehandletTid", StandardSQLTypeName.DATETIME)
                    .setMode(Field.Mode.NULLABLE).build()
            val aktorId = Field.of("aktorId", StandardSQLTypeName.STRING)
            val tekniskTid =
                Field.newBuilder("tekniskTid", StandardSQLTypeName.DATETIME)
                    .setDescription("Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.")
                    .build()
            val mottattTid = Field.newBuilder("mottattTid", StandardSQLTypeName.DATETIME)
                .setDescription("Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid.")
                .build()
            val registrertTid = Field.newBuilder("registrertTid", StandardSQLTypeName.DATETIME)
                .setDescription("Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.")
                .build()
            val versjon = Field.of("versjon", StandardSQLTypeName.STRING)
            val avsender = Field.of("avsender", StandardSQLTypeName.STRING)
            val sekvensNummer = Field.of("sekvensnummer", StandardSQLTypeName.INT64)
            return Schema.of(
                sekvensNummer,
                saksnummmer,
                behandlingUuid,
                relatertBehandlingUUid,
                behandlingType,
                behandlingStatus,
                ferdigbehandletTid,
                aktorId,
                tekniskTid,
                mottattTid,
                registrertTid,
                versjon,
                avsender
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): BQBehandling {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlingUuid = fieldValueList.get("behandlingUuid").stringValue
        val relatertBehandlingUUid =
            if (!fieldValueList.get("relatertBehandlingUUid").isNull) fieldValueList.get("relatertBehandlingUUid").stringValue else null
        val ferdigbehandletTid = fieldValueList.get("ferdigbehandletTid").timestampValue
        val tekniskTid = fieldValueList.get("tekniskTid").stringValue
        val mottattTid = fieldValueList.get("mottattTid").stringValue
        val registrertTid = fieldValueList.get("registrertTid").stringValue
        val behandlingType = fieldValueList.get("behandlingType").stringValue
        val versjon = fieldValueList.get("versjon").stringValue
        val avsender = fieldValueList.get("avsender").stringValue
        val sekvensNummer = fieldValueList.get("sekvensnummer").longValue
        val aktorId = fieldValueList.get("aktorId").stringValue

        return BQBehandling(
            saksnummer = saksnummer,
            behandlingUUID = behandlingUuid,
            relatertBehandlingUUID = relatertBehandlingUUid,
            tekniskTid = LocalDateTime.parse(tekniskTid),
            behandlingType = behandlingType,
            avsender = avsender,
            verson = versjon,
            sekvensNummer = sekvensNummer,
            aktorId = aktorId,
            mottattTid = LocalDateTime.parse(mottattTid),
            registrertTid = LocalDateTime.parse(registrertTid),
        )
    }

    override fun toRow(value: BQBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "sekvensnummer" to value.sekvensNummer,
                "saksnummer" to value.saksnummer,
                "behandlingUuid" to value.behandlingUUID,
                "relatertBehandlingUUid" to value.relatertBehandlingUUID,
                "behandlingType" to value.behandlingType,
                "aktorId" to value.aktorId,
                "tekniskTid" to value.tekniskTid.truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "mottattTid" to value.mottattTid.truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "registrertTid" to value.registrertTid.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "avsender" to value.avsender,
                "versjon" to value.verson,
            )
        )
    }
}