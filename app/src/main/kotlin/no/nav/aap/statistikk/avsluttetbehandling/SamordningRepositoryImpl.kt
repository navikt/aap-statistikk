package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.behandling.BehandlingId

class SamordningRepositoryImpl(private val dbConnection: DBConnection) : SamordningRepository {

    companion object : RepositoryFactory<SamordningRepositoryImpl> {
        override fun konstruer(connection: DBConnection): SamordningRepositoryImpl =
            SamordningRepositoryImpl(connection)
    }

    override fun lagre(behandlingId: BehandlingId, samordning: Samordning) {
        val id = behandlingId.id

        dbConnection.execute("DELETE FROM samordning_ufore WHERE behandling_id = ?") {
            setParams { setLong(1, id) }
        }
        dbConnection.execute("DELETE FROM samordning_statlig_ytelse WHERE behandling_id = ?") {
            setParams { setLong(1, id) }
        }
        dbConnection.execute("DELETE FROM samordning_avregning_andre_ytelser WHERE behandling_id = ?") {
            setParams { setLong(1, id) }
        }
        dbConnection.execute("DELETE FROM samordning_arbeidsgiver WHERE behandling_id = ?") {
            setParams { setLong(1, id) }
        }

        dbConnection.executeBatch(
            "INSERT INTO samordning_ufore (behandling_id, fra_dato, til_dato, grad) VALUES (?, ?, ?, ?)",
            samordning.uføre,
        ) {
            setParams {
                setLong(1, id)
                setLocalDate(2, it.fom)
                setLocalDate(3, it.tom)
                setInt(4, it.grad)
            }
        }

        dbConnection.executeBatch(
            "INSERT INTO samordning_statlig_ytelse (behandling_id, fra_dato, til_dato, ytelse, prosent) VALUES (?, ?, ?, ?, ?)",
            samordning.statligeYtelser,
        ) {
            setParams {
                setLong(1, id)
                setLocalDate(2, it.fom)
                setLocalDate(3, it.tom)
                setString(4, it.ytelse.name)
                setInt(5, it.prosent)
            }
        }

        dbConnection.executeBatch(
            "INSERT INTO samordning_avregning_andre_ytelser (behandling_id, fra_dato, til_dato, ytelse) VALUES (?, ?, ?, ?)",
            samordning.avregningAndreYtelser,
        ) {
            setParams {
                setLong(1, id)
                setLocalDate(2, it.fom)
                setLocalDate(3, it.tom)
                setString(4, it.ytelse.name)
            }
        }

        dbConnection.executeBatch(
            "INSERT INTO samordning_arbeidsgiver (behandling_id, fra_dato, til_dato) VALUES (?, ?, ?)",
            samordning.arbeidsgiver,
        ) {
            setParams {
                setLong(1, id)
                setLocalDate(2, it.fom)
                setLocalDate(3, it.tom)
            }
        }
    }

    override fun hent(behandlingId: BehandlingId): Samordning? {
        val id = behandlingId.id

        val behandlingExists = dbConnection.queryFirst(
            "SELECT COUNT(*) as count FROM behandling WHERE id = ?"
        ) {
            setParams { setLong(1, id) }
            setRowMapper { it.getInt("count") > 0 }
        }

        if (!behandlingExists) return null

        val uføre = dbConnection.queryList(
            "SELECT fra_dato, til_dato, grad FROM samordning_ufore WHERE behandling_id = ?"
        ) {
            setParams { setLong(1, id) }
            setRowMapper {
                Samordning.UførePeriode(
                    fom = it.getLocalDate("fra_dato"),
                    tom = it.getLocalDate("til_dato"),
                    grad = it.getInt("grad"),
                )
            }
        }

        val statligeYtelser = dbConnection.queryList(
            "SELECT fra_dato, til_dato, ytelse, prosent FROM samordning_statlig_ytelse WHERE behandling_id = ?"
        ) {
            setParams { setLong(1, id) }
            setRowMapper {
                Samordning.StatligYtelse(
                    fom = it.getLocalDate("fra_dato"),
                    tom = it.getLocalDate("til_dato"),
                    ytelse = Samordning.SamordningYtelse.valueOf(it.getString("ytelse")),
                    prosent = it.getInt("prosent"),
                )
            }
        }

        val avregningAndreYtelser = dbConnection.queryList(
            "SELECT fra_dato, til_dato, ytelse FROM samordning_avregning_andre_ytelser WHERE behandling_id = ?"
        ) {
            setParams { setLong(1, id) }
            setRowMapper {
                Samordning.AvregningAndreYtelse(
                    fom = it.getLocalDate("fra_dato"),
                    tom = it.getLocalDate("til_dato"),
                    ytelse = Samordning.AndreStatligeYtelse.valueOf(it.getString("ytelse")),
                )
            }
        }

        val arbeidsgiver = dbConnection.queryList(
            "SELECT fra_dato, til_dato FROM samordning_arbeidsgiver WHERE behandling_id = ?"
        ) {
            setParams { setLong(1, id) }
            setRowMapper {
                Samordning.Arbeidsgiver(
                    fom = it.getLocalDate("fra_dato"),
                    tom = it.getLocalDate("til_dato"),
                )
            }
        }

        return Samordning(
            uføre = uføre,
            statligeYtelser = statligeYtelser,
            avregningAndreYtelser = avregningAndreYtelser,
            arbeidsgiver = arbeidsgiver,
        )
    }
}

