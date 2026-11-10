package studyfix;

import studyfix.assignment.persistence.AssignmentRepository;
import studyfix.assignment.persistence.ExamOrganizationRepository;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.persistence.ExamOrganizationRepository.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Upgrade the retired venue schema using an isolated database, never a user's database. */
public final class ExamOnlineMigrationTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception {
        Path database=Files.createTempDirectory(Path.of("build"),"online-migration-").resolve("learning.db");
        AssignmentRepository repo=new AssignmentRepository(database);
        new ExamOrganizationRepository(repo);
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+database.toAbsolutePath())){
            update(c,"INSERT INTO p3_contests VALUES('old','class-1','Existing exam','teacher',1,1000,2000,'FINISHED','AC_COUNT_SCORE_TIME_V2','INDIVIDUAL')");
            update(c,"INSERT INTO p3_eo_exams(contest_id,code,updated_at) VALUES('old','OLD',1)");
            update(c,"INSERT INTO p3_eo_candidates(contest_id,user_id,username,student_code,display_name,class_id,class_name,started_at,finished_at,extra_minutes) VALUES('old','student','student','SV001','Student','class-1','Java',1100,1900,15)");
            update(c,"INSERT INTO p3_eo_code_drafts VALUES('old','student','Q1','saved source',1500)");
            update(c,"INSERT INTO p3_eo_drafts VALUES('draft','teacher',7,?,1)",stringify(mapOf("title","Legacy draft","classIds",List.of("class-1"),"studentIds",List.of("student"),"format","LAB","roomIds",List.of("venue"),"proctors",List.of("invigilator"))));
            update(c,"INSERT INTO p3_eo_publications(contest_id,actor_id,created_at,rows_json) VALUES('old','teacher',2000,?)",stringify(List.of(mapOf("id","receipt","userId","student","score",75,"maxScore",100,"verdict","AC","roomName","Lab A","seat",4))));
            update(c,"ALTER TABLE p3_eo_exams ADD COLUMN format TEXT NOT NULL DEFAULT 'LAB'");
            update(c,"ALTER TABLE p3_eo_candidates ADD COLUMN room_id TEXT NOT NULL DEFAULT 'venue'");
            update(c,"ALTER TABLE p3_eo_candidates ADD COLUMN seat INTEGER NOT NULL DEFAULT 4");
            update(c,"ALTER TABLE p3_eo_candidates ADD COLUMN shift TEXT NOT NULL DEFAULT 'Morning'");
            update(c,"CREATE UNIQUE INDEX p3_eo_seat_unique ON p3_eo_candidates(contest_id,room_id,seat) WHERE room_id<>'' AND seat>0");
            update(c,"CREATE TABLE p3_eo_rooms(id TEXT PRIMARY KEY,name TEXT)");
            update(c,"INSERT INTO p3_eo_rooms VALUES('venue','Lab A')");
            update(c,"CREATE TABLE p3_eo_proctors(contest_id TEXT,user_id TEXT,room_id TEXT)");
            update(c,"INSERT INTO p3_eo_proctors VALUES('old','other-teacher','venue')");
            update(c,"DELETE FROM p3_eo_migrations WHERE version=2");
        }
        new ExamOrganizationRepository(repo);
        new ExamOrganizationRepository(repo); // Restart must be idempotent.
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+database.toAbsolutePath())){
            check(rows(c,"SELECT name FROM sqlite_master WHERE name IN ('p3_eo_rooms','p3_eo_proctors','p3_eo_seat_unique')").isEmpty(),"venue tables and seat index removed");
            check(rows(c,"PRAGMA table_info(p3_eo_exams)").stream().noneMatch(r->"format".equals(r.get("name"))),"format column removed");
            check(rows(c,"PRAGMA table_info(p3_eo_candidates)").stream().noneMatch(r->Set.of("room_id","seat","shift").contains(r.get("name"))),"allocation columns removed");
            check(rows(c,"SELECT 1 FROM p3_contests WHERE id='old'").size()==1,"exam preserved");
            var student=rows(c,"SELECT * FROM p3_eo_candidates WHERE user_id='student'").getFirst();
            check(number(student,"started_at",0)==1100&&number(student,"finished_at",0)==1900&&number(student,"extra_minutes",0)==15,"participant timing and extension preserved");
            check("saved source".equals(rows(c,"SELECT source_code FROM p3_eo_code_drafts").getFirst().get("source_code")),"saved code preserved");
            var draft=rows(c,"SELECT revision,document_json FROM p3_eo_drafts").getFirst();
            var document=object(text(draft,"document_json","{}"));
            check(number(draft,"revision",0)==7&&list(document.get("studentIds")).equals(List.of("student")),"draft revision and students preserved");
            check(!document.containsKey("format")&&!document.containsKey("roomIds")&&!document.containsKey("proctors"),"legacy draft fields removed");
            var publication=rows(c,"SELECT id,created_at,rows_json FROM p3_eo_publications").getFirst();
            var grade=map(list(parse(text(publication,"rows_json","[]"))).getFirst());
            check(number(grade,"score",0)==75&&number(grade,"maxScore",0)==100&&"AC".equals(grade.get("verdict"))&&number(publication,"created_at",0)==2000,"published score and version preserved");
            check(!grade.containsKey("roomName")&&!grade.containsKey("seat"),"retired publication metadata removed");
            check(rows(c,"SELECT 1 FROM p3_eo_migrations WHERE version=2").size()==1,"migration applied exactly once");
        }
        System.out.println("ExamOnlineMigrationTest PASS: "+checks+" SQLite migration/restart checks.");
    }
}
