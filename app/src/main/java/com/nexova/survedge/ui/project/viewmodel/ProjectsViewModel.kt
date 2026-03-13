package com.nexova.survedge.ui.project.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexova.survedge.data.db.AppDatabase
import com.nexova.survedge.data.db.entity.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {

    private val projectDao = AppDatabase.getDatabase(application).projectDao()

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun createProject(
        name: String, 
        operator: String? = null,
        crs: String? = "WGS84",
        verticalDatum: String? = "Ellipsoidal height",
        distanceUnit: String? = "Meters"
    ): Long {
        return withContext(Dispatchers.IO) {
            val newProject = ProjectEntity(
                name = name,
                createdDate = System.currentTimeMillis(),
                lastModified = System.currentTimeMillis(),
                operatorName = operator,
                crs = crs,
                verticalDatum = verticalDatum,
                distanceUnit = distanceUnit
            )
            projectDao.insertProject(newProject)
        }
    }

    suspend fun exportProjectPoints(projectId: Long): String {
        return withContext(Dispatchers.IO) {
            val pointsFlow = AppDatabase.getDatabase(getApplication()).pointDao().getPointsByProject(projectId)
            // Collect the first emission from the Flow
            val points = pointsFlow.first()
            com.nexova.survedge.utils.CSVExporter.generateCSV(points)
            com.nexova.survedge.utils.CSVExporter.generateCSV(points)
        }
    }

    suspend fun importProjectPoints(projectId: Long, jsonContent: String): Int {
        return withContext(Dispatchers.IO) {
            val points = com.nexova.survedge.utils.JSONImporter.parseJSON(jsonContent, projectId)
            if (points.isNotEmpty()) {
                val db = AppDatabase.getDatabase(getApplication())
                db.pointDao().insertPoints(points)
                
                // Update project lastModified time
                val project = db.projectDao().getProjectById(projectId)
                project?.let {
                    val updatedProject = it.copy(lastModified = System.currentTimeMillis())
                    db.projectDao().updateProject(updatedProject)
                }
            }
            points.size
        }
    }
}
