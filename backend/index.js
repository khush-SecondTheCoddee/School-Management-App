const express = require('express');
const admin = require('firebase-admin');
const { adminRoutes } = require('./api/adminRoutes');
const { attendanceRoutes } = require('./api/attendanceRoutes');

if (!admin.apps.length) {
  admin.initializeApp();
}

const app = express();
app.use(express.json());
app.use('/v1', adminRoutes);
app.use('/v1', attendanceRoutes);

module.exports = { app };
