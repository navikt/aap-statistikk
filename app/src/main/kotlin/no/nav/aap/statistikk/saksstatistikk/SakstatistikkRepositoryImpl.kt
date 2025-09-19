package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.behandling.SøknadsFormat

class SakstatistikkRepositoryImpl(private val dbConnection: DBConnection) :
    SakstatistikkRepository {
    override fun lagre(bqBehandling: BQBehandling): Long {
        val sql = """
insert into saksstatistikk
(fagsystem_navn,
 behandling_uuid,
 saksnummer,
 relatert_behandling_uuid,
 relatert_fagsystem,
 behandling_type,
 aktor_id,
 teknisk_tid,
 registrert_tid,
 endret_tid,
 mottatt_tid,
 vedtak_tid,
 ferdigbehandlet_tid,
 versjon,
 avsender,
 opprettet_av,
 ansvarlig_beslutter,
 soknadsformat,
 saksbehandler,
 behandlingmetode,
 behandling_status,
 behandling_aarsak,
 behandling_resultat,
 resultat_begrunnelse,
 ansvarlig_enhet_kode,
 sak_ytelse)
values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return dbConnection.executeReturnKey(sql) {
            setParams {
                setString(1, bqBehandling.fagsystemNavn)
                setUUID(2, bqBehandling.behandlingUUID)
                setString(3, bqBehandling.saksnummer)
                setUUID(4, bqBehandling.relatertBehandlingUUID)
                setString(5, bqBehandling.relatertFagsystem)
                setString(6, bqBehandling.behandlingType)
                setString(7, bqBehandling.aktorId)
                setLocalDateTime(8, bqBehandling.tekniskTid)
                setLocalDateTime(9, bqBehandling.registrertTid)
                setLocalDateTime(10, bqBehandling.endretTid)
                setLocalDateTime(11, bqBehandling.mottattTid)
                setLocalDateTime(12, bqBehandling.vedtakTid)
                setLocalDateTime(13, bqBehandling.ferdigbehandletTid)
                setString(14, bqBehandling.versjon)
                setString(15, bqBehandling.avsender)
                setString(16, bqBehandling.opprettetAv)
                setString(17, bqBehandling.ansvarligBeslutter)
                setString(18, bqBehandling.søknadsFormat.toString())
                setString(19, bqBehandling.saksbehandler)
                setString(20, bqBehandling.behandlingMetode.toString())
                setString(21, bqBehandling.behandlingStatus)
                setString(22, bqBehandling.behandlingÅrsak)
                setString(23, bqBehandling.behandlingResultat)
                setString(24, bqBehandling.resultatBegrunnelse)
                setString(25, bqBehandling.ansvarligEnhetKode)
                setString(26, bqBehandling.sakYtelse)
            }
        }
    }

    override fun hentSisteForBehandling(
        id: Long
    ): BQBehandling {
        val sql = """
            select * from saksstatistikk where id = ?
        """.trimIndent()

        return dbConnection.queryFirst(sql) {
            setRowMapper { row ->
                BQBehandling(
                    fagsystemNavn = row.getString("fagsystem_navn"),
                    sekvensNummer = row.getLong("id"),
                    saksnummer = row.getString("saksnummer"),
                    behandlingUUID = row.getUUID("behandling_uuid"),
                    relatertBehandlingUUID = row.getUUIDOrNull("relatert_behandling_uuid"),
                    relatertFagsystem = row.getString("relatert_fagsystem"),
                    ferdigbehandletTid = row.getLocalDateTimeOrNull("ferdigbehandlet_tid"),
                    behandlingType = row.getString("behandling_type"),
                    aktorId = row.getString("aktor_id"),
                    tekniskTid = row.getLocalDateTime("teknisk_tid"),
                    registrertTid = row.getLocalDateTime("registrert_tid"),
                    mottattTid = row.getLocalDateTime("mottatt_tid"),
                    endretTid = row.getLocalDateTime("endret_tid"),
                    versjon = row.getString("versjon"),
                    avsender = row.getString("avsender"),
                    opprettetAv = row.getString("opprettet_av"),
                    saksbehandler = row.getString("saksbehandler"),
                    vedtakTid = row.getLocalDateTimeOrNull("vedtak_tid"),
                    søknadsFormat = SøknadsFormat.valueOf(row.getString("soknadsformat")),
                    behandlingMetode = BehandlingMetode.valueOf(
                        row.getString("behandlingmetode")
                    ),
                    ansvarligBeslutter = row.getString("ansvarlig_beslutter"),
                    behandlingStatus = row.getString("behandling_status"),
                    behandlingÅrsak = row.getString("behandling_aarsak"),
                    ansvarligEnhetKode = row.getString("ansvarlig_enhet_kode"),
                    sakYtelse = row.getString("sak_ytelse"),
                    behandlingResultat = row.getString("behandling_resultat"),
                    resultatBegrunnelse = row.getString("resultat_begrunnelse")
                )
            }
            setParams {
                setLong(1, id)
            }
        }
    }

}