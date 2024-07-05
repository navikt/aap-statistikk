package no.nav.aap.statistikk

interface IObserver<E> {
    fun update(data: E)
}