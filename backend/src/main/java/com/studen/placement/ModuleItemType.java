package com.studen.placement;

// What a placement module item points at. Each value maps to one existing content table rather
// than to a new placement-owned copy of it:
//   QUESTION             -> com.studen.questionbank.Question (MCQ / aptitude / technical / case study)
//   PRACTICAL_ASSESSMENT -> com.studen.practical.PracticalAssessment (coding and other practicals,
//                           which already own languages, starter code, test cases and limits)
//   RESOURCE             -> com.studen.resource.Resource (learning material inside a prep series)
public enum ModuleItemType {
    QUESTION,
    PRACTICAL_ASSESSMENT,
    RESOURCE
}
