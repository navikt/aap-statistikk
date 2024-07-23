package no.nav.aap.statistikk

internal interface ISubject<E : Any> {
    fun registerObserver(observer: IObserver<E>)
    fun removeObserver(observer: IObserver<E>)
    suspend fun notifyObservers(data: E)
}

internal interface IObserver<out E> {
    suspend fun update(data: @UnsafeVariance E)
}

internal data class Observed<E>(val observed: E)

private val xx = object : ISubject<Any> {
    override fun registerObserver(observer: IObserver<Any>) {
        TODO("Not yet implemented")
    }

    override fun removeObserver(observer: IObserver<Any>) {
        TODO("Not yet implemented")
    }

    override suspend fun notifyObservers(data: Any) {
        TODO("Not yet implemented")
    }
}

private val yy = object : IObserver<String> {
    override suspend fun update(data: String) {
        TODO("Not yet implemented")
    }
}

private class A {
    init {
        xx.registerObserver(yy)
    }
}