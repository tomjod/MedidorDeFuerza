package com.tomjod.medidorfuerza.di

import com.tomjod.medidorfuerza.data.ble.BleRepository
import com.tomjod.medidorfuerza.data.ble.BluetoothClassicServiceManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBleRepository(
        bleServiceManager: BluetoothClassicServiceManager
    ): BleRepository
}
