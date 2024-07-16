package no.nav.aap.statistikk

interface IObserver<E> {
    suspend fun update(data: E)
}