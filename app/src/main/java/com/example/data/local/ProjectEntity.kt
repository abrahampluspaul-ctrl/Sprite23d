package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val spritePath: String,
    val rigPresetName: String,
    val meshDensityName: String,
    val vertexCount: Int,
    val boneCount: Int,
    val analysisJson: String,
    val bonesJson: String,
    val materialJson: String,
    val physicsJson: String,
    val animationJson: String,
    val updatedAt: Long
)
