package com.nexova.survedge.ui.mapping.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexova.survedge.data.db.AppDatabase
import com.nexova.survedge.data.db.entity.LineEntity
import com.nexova.survedge.data.db.entity.LinePointCrossRef
import com.nexova.survedge.data.db.entity.LineWithPoints
import com.nexova.survedge.data.db.entity.PointEntity
import com.nexova.survedge.data.db.entity.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MappingViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val pointDao = db.pointDao()
    private val lineDao = db.lineDao()
    private val projectDao = db.projectDao()

    // Currently active project ID. 
    // TODO: persist this in SharedPreferences/DataStore. For now, default to a dummy or first project.
    private val _currentProjectId = MutableStateFlow<Long?>(null)
    val currentProjectId = _currentProjectId.asStateFlow()

    // Current Project Entity
    val currentProject = _currentProjectId.flatMapLatest { id ->
        if (id == null) flowOf(null) else flowOf(projectDao.getProjectById(id)) // getProjectById is suspend, flow needed for reactive? 
        // Actually projectDao.getAllProjects() returns Flow. Let's make a simple state for now.
        // For simplicity, let's just expose points for the *active* project.
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Points for the current project
    val currentPoints = _currentProjectId.flatMapLatest { id ->
        if (id != null) {
            pointDao.getPointsByProject(id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Lines for the current project (re-ordered to respect orderIndex)
    val currentLines = _currentProjectId.flatMapLatest { id ->
        if (id != null) {
            lineDao.getLinesWithPoints(id).map { linesWithPoints ->
                linesWithPoints.map { lineWithPoints ->
                    // Re-order points by orderIndex from the cross-ref table
                    val crossRefs = lineDao.getLinePointCrossRefsByLinePk(lineWithPoints.line.pk)
                    if (crossRefs.isNotEmpty()) {
                        val pointsByPk = lineWithPoints.points.associateBy { it.pk }
                        val orderedPoints = crossRefs.mapNotNull { ref -> pointsByPk[ref.pointPk] }
                        lineWithPoints.copy(points = orderedPoints)
                    } else {
                        lineWithPoints
                    }
                }
            }.flowOn(Dispatchers.IO)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize with a default project if none exists, just to have a working state for the user
        viewModelScope.launch(Dispatchers.IO) {
            val projects = projectDao.getAllProjects() 
            // getAllProjects returns Flow, we can't collect it simply here for check. 
            // Let's do a quick check or insert default.
            
            // Standardizing: Insert a default project if DB is empty?
            // For now, let's create a "Default Project" if we don't have an ID set.
            if (_currentProjectId.value == null) {
                // Ideally check DB. simplified:
                val defaultProject = ProjectEntity(
                    name = "Default Project",
                    createdDate = System.currentTimeMillis(),
                    lastModified = System.currentTimeMillis()
                )
                // We'd need to check if it exists first to avoid dupes on every run if not persisted.
                // Assuming ID 1 is default for this MVP.
                _currentProjectId.value = 1L
                val existing = projectDao.getProjectById(1L)
                if (existing == null) {
                   projectDao.insertProject(defaultProject.copy(id = 1L))
                }
            }
        }
    }

    fun setProjectId(id: Long) {
        _currentProjectId.value = id
    }

    private fun updateProjectTimestamp(targetProjectId: Long? = null) {
        val projectId = targetProjectId ?: _currentProjectId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val project = projectDao.getProjectById(projectId)
            project?.let {
                projectDao.updateProject(it.copy(lastModified = System.currentTimeMillis()))
            }
        }
    }

    fun savePoint(point: PointEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = pointDao.getPointByCode(point.projectId, point.id)
            val pointToSave = if (existing != null) {
                point.copy(pk = existing.pk)
            } else {
                point
            }
            pointDao.insertPoint(pointToSave)
            updateProjectTimestamp(point.projectId)
        }
    }


    fun deletePoint(point: PointEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            pointDao.deletePoint(point)
            updateProjectTimestamp(point.projectId)
        }
    }
    
    fun deletePointById(pointId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // This method might need adjustment as we don't know the project ID easily if just passing ID string.
            // But PointEntity has projectId. 
            // If calling with just ID, it's ambiguous now across projects.
            // Assuming this is called within current project context.
            val projectId = _currentProjectId.value ?: return@launch
            val pt = pointDao.getPointByCode(projectId, pointId)
            pt?.let { 
                pointDao.deletePoint(it)
                updateProjectTimestamp(it.projectId)
            }
        }
    }

    fun saveLine(line: LineEntity, points: List<PointEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Upsert Line
            val existingLine = lineDao.getLineByCode(line.projectId, line.id)
            val lineToSave = if (existingLine != null) {
                line.copy(pk = existingLine.pk)
            } else {
                line
            }
            val linePk = lineDao.insertLine(lineToSave)

            // 2. Upsert Points to get their PKs (if they aren't already valid)
            val crossRefs = points.mapIndexed { index, point ->
                val existingPoint = pointDao.getPointByCode(point.projectId, point.id)
                val pointToSave = if (existingPoint != null) {
                    point.copy(pk = existingPoint.pk)
                } else {
                    point
                }
                val pointPk = pointDao.insertPoint(pointToSave)
                
                LinePointCrossRef(
                    linePk = linePk,
                    pointPk = pointPk,
                    orderIndex = index
                )
            }
            
            // 3. Clear old crossrefs and insert new ones
            // We can't use updateLineWithPoints efficiently because we did manual ref resolution
            lineDao.clearLinePoints(linePk)
            lineDao.insertLinePointCrossRefs(crossRefs)
            
            updateProjectTimestamp(line.projectId)
        }
    }
    
    fun saveLineMetadata(line: LineEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingLine = lineDao.getLineByCode(line.projectId, line.id)
            val lineToSave = if (existingLine != null) {
                line.copy(pk = existingLine.pk)
            } else {
                line
            }
            lineDao.insertLine(lineToSave)
            updateProjectTimestamp(line.projectId)
        }
    }
    
    fun deleteLine(line: LineEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            lineDao.deleteLine(line)
            updateProjectTimestamp(line.projectId)
        }
    }
}
