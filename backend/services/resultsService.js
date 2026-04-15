const crypto = require('crypto');

const RESULT_STATUSES = Object.freeze({
  DRAFT: 'DRAFT',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  PUBLISHED: 'PUBLISHED',
  REJECTED: 'REJECTED',
});

function validateExamModel(examModel) {
  if (!examModel || typeof examModel !== 'object') {
    return 'exam model is required';
  }

  const { name, gradingSchema, subjects } = examModel;
  if (!name || typeof name !== 'string') {
    return 'exam name is required';
  }

  if (!Array.isArray(subjects) || subjects.length === 0) {
    return 'subjects must be a non-empty array';
  }

  for (const subject of subjects) {
    if (!subject?.subjectCode || !Array.isArray(subject?.components) || subject.components.length === 0) {
      return 'each subject must include subjectCode and non-empty components';
    }

    const totalWeight = subject.components.reduce((sum, component) => {
      const weight = Number(component.weightage || 0);
      return sum + weight;
    }, 0);

    if (Math.abs(totalWeight - 100) > 0.0001) {
      return `components for ${subject.subjectCode} must total 100 weightage`;
    }
  }

  if (!Array.isArray(gradingSchema) || gradingSchema.length === 0) {
    return 'gradingSchema must be a non-empty array';
  }

  return null;
}

function validateMarksPayload(entry, examModel) {
  if (!entry?.studentId) {
    return 'studentId is required';
  }

  if (!Array.isArray(entry?.subjectMarks) || entry.subjectMarks.length === 0) {
    return 'subjectMarks must be a non-empty array';
  }

  const modelBySubject = new Map((examModel.subjects || []).map((s) => [s.subjectCode, s]));

  for (const subjectMark of entry.subjectMarks) {
    const subjectModel = modelBySubject.get(subjectMark.subjectCode);
    if (!subjectModel) {
      return `unknown subjectCode: ${subjectMark.subjectCode}`;
    }

    const componentByName = new Map((subjectModel.components || []).map((c) => [c.name, c]));
    if (!Array.isArray(subjectMark.components) || subjectMark.components.length === 0) {
      return `subject ${subjectMark.subjectCode} must include components marks`;
    }

    for (const componentMark of subjectMark.components) {
      const definition = componentByName.get(componentMark.name);
      if (!definition) {
        return `invalid component ${componentMark.name} for subject ${subjectMark.subjectCode}`;
      }

      if (typeof componentMark.obtained !== 'number' || componentMark.obtained < 0) {
        return `invalid obtained marks for ${subjectMark.subjectCode}:${componentMark.name}`;
      }

      if (typeof definition.maxMarks !== 'number' || definition.maxMarks <= 0) {
        return `invalid maxMarks in exam model for ${subjectMark.subjectCode}:${componentMark.name}`;
      }

      if (componentMark.obtained > definition.maxMarks) {
        return `obtained marks exceed max for ${subjectMark.subjectCode}:${componentMark.name}`;
      }
    }
  }

  return null;
}

function computeWeightedPercentage(subjectMark, subjectModel) {
  const componentMarksByName = new Map((subjectMark.components || []).map((c) => [c.name, c]));

  return (subjectModel.components || []).reduce((sum, component) => {
    const mark = componentMarksByName.get(component.name);
    const obtained = mark ? Number(mark.obtained) : 0;
    const normalized = component.maxMarks > 0 ? obtained / component.maxMarks : 0;
    return sum + normalized * Number(component.weightage || 0);
  }, 0);
}

function calculateGrade(percentage, gradingSchema) {
  const sorted = [...gradingSchema].sort((a, b) => Number(b.minPercentage || 0) - Number(a.minPercentage || 0));
  const match = sorted.find((bucket) => percentage >= Number(bucket.minPercentage || 0));
  return match ? match.grade : 'NA';
}

function buildResultSummary(entry, examModel) {
  const subjectDefinitions = new Map((examModel.subjects || []).map((subject) => [subject.subjectCode, subject]));
  const subjectSummaries = (entry.subjectMarks || []).map((subjectMark) => {
    const subjectModel = subjectDefinitions.get(subjectMark.subjectCode);
    const weightedPercentage = computeWeightedPercentage(subjectMark, subjectModel);

    return {
      subjectCode: subjectMark.subjectCode,
      weightedPercentage: Number(weightedPercentage.toFixed(2)),
      grade: calculateGrade(weightedPercentage, examModel.gradingSchema || []),
      components: subjectMark.components,
    };
  });

  const overallPercentage = subjectSummaries.length === 0
    ? 0
    : subjectSummaries.reduce((sum, item) => sum + item.weightedPercentage, 0) / subjectSummaries.length;

  return {
    subjects: subjectSummaries,
    overallPercentage: Number(overallPercentage.toFixed(2)),
    overallGrade: calculateGrade(overallPercentage, examModel.gradingSchema || []),
  };
}

function buildPublishAuditHash(payload) {
  return crypto.createHash('sha256').update(JSON.stringify(payload)).digest('hex');
}

function toCsv(resultEntry) {
  const lines = [
    'subject_code,weighted_percentage,grade',
    ...(resultEntry.summary?.subjects || []).map((subject) => (
      `${subject.subjectCode},${subject.weightedPercentage},${subject.grade}`
    )),
    `OVERALL,${resultEntry.summary?.overallPercentage || 0},${resultEntry.summary?.overallGrade || 'NA'}`,
  ];

  return `${lines.join('\n')}\n`;
}

function toSimplePdfBuffer(resultEntry) {
  const lines = [
    'Report Card',
    `Student: ${resultEntry.studentId}`,
    `Exam: ${resultEntry.examName}`,
    `Overall: ${resultEntry.summary?.overallPercentage || 0}% (${resultEntry.summary?.overallGrade || 'NA'})`,
  ];

  const pageText = lines.map((line, index) => `BT /F1 12 Tf 50 ${780 - index * 20} Td (${line.replace(/[()]/g, '')}) Tj ET`).join('\n');

  const objects = [];
  const addObj = (content) => {
    objects.push(content);
    return objects.length;
  };

  const fontObj = addObj('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>');
  const contentObj = addObj(`<< /Length ${pageText.length} >>\nstream\n${pageText}\nendstream`);
  const pageObj = addObj(`<< /Type /Page /Parent 4 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 ${fontObj} 0 R >> >> /Contents ${contentObj} 0 R >>`);
  const pagesObj = addObj(`<< /Type /Pages /Kids [${pageObj} 0 R] /Count 1 >>`);
  const catalogObj = addObj(`<< /Type /Catalog /Pages ${pagesObj} 0 R >>`);

  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((obj, index) => {
    offsets.push(pdf.length);
    pdf += `${index + 1} 0 obj\n${obj}\nendobj\n`;
  });

  const xrefStart = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  offsets.slice(1).forEach((offset) => {
    pdf += `${offset.toString().padStart(10, '0')} 00000 n \n`;
  });

  pdf += `trailer\n<< /Size ${objects.length + 1} /Root ${catalogObj} 0 R >>\nstartxref\n${xrefStart}\n%%EOF`;
  return Buffer.from(pdf, 'utf8');
}

module.exports = {
  RESULT_STATUSES,
  validateExamModel,
  validateMarksPayload,
  buildResultSummary,
  buildPublishAuditHash,
  toCsv,
  toSimplePdfBuffer,
};
