package no.nav.aap.statistikk.tilbakekreving

import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProviderJobbSpesifikasjon
import no.nav.aap.statistikk.sak.SakRepository

class LagreTilbakekrevingHendelseJobbUtfører(
    private val repository: TilbakekrevingHendelseRepository,
    private val sakRepository: SakRepository,
) : JobbUtfører {

    override fun utfør(input: JobbInput) {
        val hendelse = input.payload<TilbakekrevingHendelse>()
        val sak = requireNotNull(sakRepository.hentSakEllernull(hendelse.saksnummer)) {
            "Fant ikke sak med saksnummer ${hendelse.saksnummer.value}"
        }
        repository.lagre(checkNotNull(sak.id), hendelse)
    }
}

class LagreTilbakekrevingHendelseJobb : ProviderJobbSpesifikasjon {
    override fun konstruer(repositoryProvider: RepositoryProvider): JobbUtfører {
        return LagreTilbakekrevingHendelseJobbUtfører(
            repository = repositoryProvider.provide(),
            sakRepository = repositoryProvider.provide(),
        )
    }

    override val type: String = "statistikk.lagreTilbakekrevingHendelse"
    override val navn: String = "Lagre tilbakekrevingshendelse"
    override val beskrivelse: String = "Lagrer mottatt tilbakekrevingshendelse til databasen"
}
