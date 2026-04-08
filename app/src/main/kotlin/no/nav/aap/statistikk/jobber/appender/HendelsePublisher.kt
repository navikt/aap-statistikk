package no.nav.aap.statistikk.jobber.appender

interface HendelsePublisher {
    fun publiser(hendelse: StatistikkHendelse)
}
