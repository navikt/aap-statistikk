package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.avsluttetbehandling.api.tilDomene
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository

class LagreAvsluttertBehandlingJobb(
    val bQRepository: BQRepository
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        val avsluttetBehandlingService = AvsluttetBehandlingService(
            transactionExecutor = FellesKomponentConnectionExecutor(connection),
            tilkjentYtelseRepositoryFactory = object : Factory<TilkjentYtelseRepository> {
                override fun create(dbConnection: DBConnection) =
                    TilkjentYtelseRepository(connection)
            },
            beregningsgrunnlagRepositoryFactory = object :
                Factory<BeregningsgrunnlagRepository> {
                override fun create(dbConnection: DBConnection): BeregningsgrunnlagRepository {
                    return BeregningsgrunnlagRepository(connection)
                }
            },
            vilkårsResultatRepositoryFactory = object : Factory<VilkårsresultatRepository> {
                override fun create(dbConnection: DBConnection): VilkårsresultatRepository {
                    return VilkårsresultatRepository(connection)
                }
            },
            bqRepository = bQRepository
        )
        return LagreAvsluttetBehandlingPostgresJobbUtfører(avsluttetBehandlingService)
    }

    override fun type(): String {
        return "prosesserAvsluttetBehandling"
    }

    override fun navn(): String {
        return "Prosesser avsluttet behandling"
    }

    override fun beskrivelse(): String {
        return "Prosesser avsluttet behandling"
    }
}

class LagreAvsluttetBehandlingPostgresJobbUtfører(private val avsluttetBehandlingService: AvsluttetBehandlingService) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<AvsluttetBehandlingDTO>(input.payload())

        avsluttetBehandlingService.lagre(dto.tilDomene())
    }
}