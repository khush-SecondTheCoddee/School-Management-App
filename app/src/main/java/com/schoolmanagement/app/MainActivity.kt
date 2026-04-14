package com.schoolmanagement.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SchoolManagementApp()
            }
        }
    }
}

enum class UserRole { Student, Teacher, Management, Parent }

data class Student(
    val name: String,
    val grade: String,
    val attendance: Int,
    val gpa: Double,
    val advisor: String
)

data class Teacher(
    val name: String,
    val subject: String,
    val classes: Int,
    val feedbackScore: Double
)

data class Announcement(
    val title: String,
    val body: String,
    val audience: String
)

class SchoolViewModel {
    val students = mutableStateListOf(
        Student("Aarav Patel", "10-A", attendance = 97, gpa = 3.9, advisor = "Mrs. Brown"),
        Student("Mia Johnson", "9-C", attendance = 94, gpa = 3.7, advisor = "Mr. Clark"),
        Student("Noah Garcia", "12-B", attendance = 99, gpa = 4.0, advisor = "Mrs. Li")
    )

    val teachers = mutableStateListOf(
        Teacher("Mrs. Brown", "Mathematics", classes = 6, feedbackScore = 4.8),
        Teacher("Mr. Clark", "Science", classes = 5, feedbackScore = 4.6),
        Teacher("Mrs. Li", "Computer Science", classes = 4, feedbackScore = 4.9)
    )

    val announcements = mutableStateListOf(
        Announcement("PTM Week", "Parent Teacher Meetings start from Monday 10 AM.", "All"),
        Announcement("AI Lab Upgrade", "New robotics kits are available in lab 2.", "Students"),
        Announcement("Payroll Processed", "Salary slips are now available in the staff portal.", "Teachers")
    )

    fun avgAttendance(): Int = students.map { it.attendance }.average().toInt()
    fun avgGpa(): Double = students.map { it.gpa }.average()
    fun avgTeacherScore(): Double = teachers.map { it.feedbackScore }.average()

    fun addDailyAnnouncement() {
        announcements.add(
            0,
            Announcement(
                title = "Daily Bulletin",
                body = "Keep attendance above 95% and submit assignments on time.",
                audience = "All"
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolManagementApp() {
    val vm = remember { SchoolViewModel() }
    var selectedRole by remember { mutableStateOf(UserRole.Student) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("School Management System") })
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            RoleSwitcher(selectedRole = selectedRole, onRoleChanged = { selectedRole = it })
            Spacer(modifier = Modifier.height(12.dp))
            OverviewBoard(vm = vm)
            Spacer(modifier = Modifier.height(12.dp))
            ActionBar(vm = vm)
            Spacer(modifier = Modifier.height(12.dp))

            when (selectedRole) {
                UserRole.Student -> StudentDashboard(vm)
                UserRole.Teacher -> TeacherDashboard(vm)
                UserRole.Management -> ManagementDashboard(vm)
                UserRole.Parent -> ParentDashboard(vm)
            }
        }
    }
}

@Composable
fun RoleSwitcher(selectedRole: UserRole, onRoleChanged: (UserRole) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        UserRole.entries.forEachIndexed { index, role ->
            SegmentedButton(
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = UserRole.entries.size
                ),
                onClick = { onRoleChanged(role) },
                selected = role == selectedRole,
                label = { Text(role.name) }
            )
        }
    }
}

@Composable
fun OverviewBoard(vm: SchoolViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard("Avg Attendance", "${vm.avgAttendance()}%", Modifier.weight(1f), Color(0xFFE8F5E9))
        MetricCard("Avg GPA", "${"%.2f".format(vm.avgGpa())}", Modifier.weight(1f), Color(0xFFE3F2FD))
        MetricCard("Teacher Rating", "${"%.1f".format(vm.avgTeacherScore())}/5", Modifier.weight(1f), Color(0xFFFFF8E1))
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier, color: Color) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .background(color)
                .padding(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActionBar(vm: SchoolViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Live Announcements", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { vm.addDailyAnnouncement() }) {
            Text("Publish Bulletin")
        }
    }
}

@Composable
fun StudentDashboard(vm: SchoolViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Student Portal") }
        items(vm.students) { student ->
            InfoCard(
                title = "${student.name} • ${student.grade}",
                lines = listOf(
                    "Attendance: ${student.attendance}%",
                    "GPA: ${student.gpa}",
                    "Advisor: ${student.advisor}"
                )
            )
        }
    }
}

@Composable
fun TeacherDashboard(vm: SchoolViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Teacher Workspace") }
        items(vm.teachers) { teacher ->
            InfoCard(
                title = teacher.name,
                lines = listOf(
                    "Subject: ${teacher.subject}",
                    "Classes managed: ${teacher.classes}",
                    "Feedback score: ${teacher.feedbackScore}/5"
                )
            )
        }
    }
}

@Composable
fun ManagementDashboard(vm: SchoolViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Management Command Center") }
        item {
            InfoCard(
                title = "Operational Health",
                lines = listOf(
                    "Total Students: ${vm.students.size}",
                    "Total Teachers: ${vm.teachers.size}",
                    "Announcements sent: ${vm.announcements.size}"
                )
            )
        }

        items(vm.announcements) { announcement ->
            InfoCard(
                title = announcement.title,
                lines = listOf(announcement.body, "Audience: ${announcement.audience}")
            )
        }
    }
}

@Composable
fun ParentDashboard(vm: SchoolViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Parent Companion") }
        items(vm.students) { student ->
            InfoCard(
                title = "Progress: ${student.name}",
                lines = listOf(
                    "Current Grade: ${student.grade}",
                    "Attendance: ${student.attendance}%",
                    "Advisor Contact: ${student.advisor}"
                )
            )
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Divider(modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun InfoCard(title: String, lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
