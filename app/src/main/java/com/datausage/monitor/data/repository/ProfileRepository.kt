package com.datausage.monitor.data.repository

import com.datausage.monitor.data.local.db.dao.ProfileDao
import com.datausage.monitor.data.local.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {
    fun getAllActiveProfiles(): Flow<List<ProfileEntity>> = profileDao.getAllActiveProfiles()

    fun getAllProfiles(): Flow<List<ProfileEntity>> = profileDao.getAllProfiles()

    suspend fun getById(id: Long): ProfileEntity? = profileDao.getById(id)

    suspend fun create(name: String, pin: String? = null): Long {
        return profileDao.insert(
            ProfileEntity(name = name, pin = pin)
        )
    }

    suspend fun update(profile: ProfileEntity) = profileDao.update(profile)

    suspend fun delete(profile: ProfileEntity) = profileDao.delete(profile)

    suspend fun deleteAll() = profileDao.deleteAll()
}
