package no.nav.aap.statistikk.sak

import com.google.cloud.bigquery.*
import no.nav.aap.statistikk.bigquery.BQTable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/*
* Mangler:
*  - relatertBehandlingId
*  - registrertTid
*  - ferdigBehandletTid
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
            val behandlingType = Field.of("behandlingType", StandardSQLTypeName.STRING)
            val aktorId = Field.of("aktorId", StandardSQLTypeName.STRING)
            val tekniskTid =
                Field.newBuilder("tekniskTid", StandardSQLTypeName.DATETIME)
                    .setDescription("Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.")
                    .build()
            val mottattTid = Field.newBuilder("mottattTid", StandardSQLTypeName.DATETIME)
                .setDescription("Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid.")
                .build()
            val versjon = Field.of("versjon", StandardSQLTypeName.STRING)
            val avsender = Field.of("avsender", StandardSQLTypeName.STRING)
            val sekvensNummer = Field.of("sekvensnummer", StandardSQLTypeName.INT64)
            return Schema.of(
                sekvensNummer,
                saksnummmer,
                behandlingUuid,
                behandlingType,
                aktorId,
                tekniskTid,
                mottattTid,
                versjon,
                avsender
            )
        }

    override fun parseRow(fieldValueList: FieldValueList): BQBehandling {
        val saksnummer = fieldValueList.get("saksnummer").stringValue
        val behandlingUuid = fieldValueList.get("behandlingUuid").stringValue
        val tekniskTid = fieldValueList.get("tekniskTid").stringValue
        val mottattTid = fieldValueList.get("mottattTid").stringValue
        val behandlingType = fieldValueList.get("behandlingType").stringValue
        val versjon = fieldValueList.get("versjon").stringValue
        val avsender = fieldValueList.get("avsender").stringValue
        val sekvensNummer = fieldValueList.get("sekvensnummer").longValue
        val aktorId = fieldValueList.get("aktorId").stringValue

        return BQBehandling(
            saksnummer = saksnummer,
            behandlingUUID = behandlingUuid,
            tekniskTid = LocalDateTime.parse(tekniskTid),
            behandlingType = behandlingType,
            avsender = avsender,
            verson = versjon,
            sekvensNummer = sekvensNummer,
            aktorId = aktorId,
            mottattTid = LocalDateTime.parse(mottattTid)
        )
    }

    override fun toRow(value: BQBehandling): InsertAllRequest.RowToInsert {
        return InsertAllRequest.RowToInsert.of(
            mapOf(
                "sekvensnummer" to value.sekvensNummer,
                "saksnummer" to value.saksnummer,
                "behandlingUuid" to value.behandlingUUID,
                "behandlingType" to value.behandlingType,
                "aktorId" to value.aktorId,
                "tekniskTid" to value.tekniskTid.truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "mottattTid" to value.mottattTid.truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "avsender" to value.avsender,
                "versjon" to value.verson,
            )
        )
    }
}