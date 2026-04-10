package no.nav.aap.statistikk.testutils

import no.nav.aap.statistikk.jobber.appender.HendelsePublisher
import no.nav.aap.statistikk.jobber.appender.StatistikkHendelse

class FakeHendelsePublisher : HendelsePublisher {
    val hendelser = mutableListOf<StatistikkHendelse>()

    override fun publiser(hendelse: StatistikkHendelse) {
        hendelser.add(hendelse)
    }
}
