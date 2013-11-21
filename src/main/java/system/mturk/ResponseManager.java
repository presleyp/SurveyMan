package system.mturk;

import com.amazonaws.mturk.addon.BatchItemCallback;
import com.amazonaws.mturk.requester.*;
import com.amazonaws.mturk.requester.Comparator;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.InternalServiceException;
import com.amazonaws.mturk.service.exception.ObjectAlreadyExistsException;
import com.amazonaws.mturk.service.exception.ObjectDoesNotExistException;
import com.amazonaws.mturk.service.exception.ServiceException;
import csv.CSVLexer;
import csv.CSVParser;
import org.apache.log4j.Logger;
import survey.Survey;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.*;
import java.util.*;
import qc.QC;
import survey.SurveyException;
import survey.SurveyResponse;
import system.Gensym;

import static java.text.MessageFormat.*;

/**
 * ResponseManager communicates with Mechanical Turk. This class contains methods to query the status of various HITs,
 * update current HITs, and pull results into a local database of responses for use inside another program.
 *
 */

public class ResponseManager {

    private static final Logger LOGGER = Logger.getLogger(ResponseManager.class);
    public static RequesterService service = SurveyPoster.service;
    final protected static long maxAutoApproveDelay = 2592000l;
    final protected static int maxwaittime = 60;
    final private static Gensym gensym = new Gensym("qual");



    /**
    * A map of the surveys launched during this session to their results.
    */
    public static HashMap<String, Record> manager = new HashMap<String, Record>();


    protected static void chill(int seconds){
        try {
            Thread.sleep(seconds*1000);
        } catch (InterruptedException e) {}
    }
    
    private static boolean overTime(String name, int waittime){
        if (waittime > ResponseManager.maxwaittime){
          LOGGER.warn(String.format("Wait time in %s has exceeded max wait time. Cancelling request.", name));
          return true;
        } else return false;
    }


    //************** Wrapped Calls to MTurk ******************//


    public static HIT getHIT(String hitid){
        String name = "getHIT";
        int waittime = 1;
        while (true) {
            synchronized (service) {
                try {
                    HIT hit = service.getHIT(hitid);
                    LOGGER.info(String.format("Retrieved HIT %s", hit.getHITId()));
                    return hit;
                } catch (InternalServiceException ise) {
                    if (overTime(name, waittime)) {
                        LOGGER.error(String.format("%s ran over time", name));
                        return null;
                    }
                    LOGGER.warn(format("{0} {1}", name, ise));
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }

    public static Assignment[] getAllAssignmentsForHIT(String hitid, AssignmentStatus[] statuses){
        String name = "getAllAssignmentsForHIT";
        int waittime = 1;
        while (true) {
            synchronized (service) {
                try {
                    Assignment[] hit = service.getAllAssignmentsForHIT(hitid, statuses);
                    LOGGER.info(String.format("Retrieved %d assignments for %s", hit.length, hitid));
                    return hit;
                } catch (InternalServiceException ise) {
                    if (overTime(name, waittime)) {
                        LOGGER.error(String.format("%s ran over time", name));
                        return null;
                    }
                    LOGGER.warn(format("{0} {1}", name, ise));
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }

    public static Assignment[] getAllAssignmentsForHIT(String hitID) {
        return getAllAssignmentsForHIT(hitID, new AssignmentStatus[]{AssignmentStatus.Rejected, AssignmentStatus.Approved, AssignmentStatus.Submitted});
    }

    public static void approveRejectedAssignment(String assignmentId) {
        String name = "approveRejectedAssignment";
        int waittime = 1;
        while (true) {
            synchronized (service) {
                try {
                    service.approveRejectedAssignment(assignmentId, "This assignment was incorrectly rejected.");
                    LOGGER.info(String.format("Approved assignment %s", assignmentId));
                    return;
                } catch (InternalServiceException ise) {
                    if (overTime(name, waittime)) {
                        LOGGER.error(String.format("%s ran over time", name));
                        return;
                    }
                    LOGGER.warn(format("{0} {1}", name, ise));
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }

    public static void grantBonus(String workerId, double amount, String assignmentId, String message) {
        String name = "grantBonus";
        int waittime = 1;
        while (true) {
            synchronized (service) {
                try {
                    service.grantBonus(workerId, amount, assignmentId, message);
                    LOGGER.info(String.format("Granted bonus of %f to %s", amount, workerId));
                    return;
                } catch (InternalServiceException ise) {
                    if (overTime(name, waittime)) {
                        LOGGER.error(String.format("%s ran over time", name));
                        return;
                    }
                    LOGGER.warn(format("{0} {1}", name, ise));
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }

    public static HIT[] getAllReviewableHITs(String hitTypeId){
        String name = "getAllReviewableHITs";
        int waittime = 1;
        while (true) {
            synchronized (service) {
                try {
                    HIT[] hits = service.getAllReviewableHITs(hitTypeId);
                    LOGGER.info(String.format("Retrieved %d HIT in %s", hits.length, name));
                    return hits;
                } catch (InternalServiceException ise) {
                    if (overTime(name, waittime)) {
                        LOGGER.error(String.format("%s ran over time", name));
                        return null;
                    }
                    System.out.println(ise.getMessage());
                    LOGGER.warn(format("{0} {1}", name, ise));
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }
    private static List<Assignment> getAllAssignmentsForHIT(HIT hit) {
        String name = "getAllAssignmentsForHIT";
        int waittime = 1;
        while (true) {
            synchronized (service) {
                try {
                    Assignment[] hitAssignments = service.getAllAssignmentsForHIT(hit.getHITId());
                    List<Assignment> assignments = new LinkedList<Assignment>();
                    boolean addAll = assignments.addAll(Arrays.asList(hitAssignments));
                    if (addAll)
                        LOGGER.info(String.format("Retrieved %d assignments for HIT %s", hitAssignments.length, hit.getHITId()));
                    return assignments;
                } catch (InternalServiceException ise) { 
                  LOGGER.warn(format("{0} {1}", name, ise));
                  chill(waittime); 
                  waittime *= 2;
                }
            }
        }
    }

    public static HIT[] searchAllHITs () {
        String name = "searchAllHITs";
        int waittime = 1;
        while (true) {
            synchronized (service) {
                try{
                    HIT[] hits = service.searchAllHITs();
                    System.out.println(String.format("Found %d HITs", hits.length));
                    LOGGER.info(String.format("Found %d HITs", hits.length));
                    return hits;
                } catch (InternalServiceException ise) {
                    LOGGER.warn(format("{0} {1}", name, ise));
                    if (overTime(name, waittime))
                      return null;
                    chill(waittime);
                    waittime = waittime*2;
                }
            }
        }
    }

    private static void extendHIT(String hitd, Integer maxAssignmentsIncrement, Long expirationIncrementInSeconds) {
        String name = "extendHIT";
        int waitTime = 1;
        while (true){
            try {
                service.extendHIT(hitd, maxAssignmentsIncrement, expirationIncrementInSeconds);
                return;
            } catch (InternalServiceException ise) {
                LOGGER.warn(format("{0} {1}", name, ise));
                if (overTime(name, waitTime))
                    return;
                chill(waitTime);
                waitTime = 2 * waitTime;
            }
        }
    }

    private static void deleteHITs(List<String> hitids) {
        String name = "deleteHITs";
        synchronized (service) {
            for (String hitid : hitids) {
                int wait = 1;
                while (true) {
                    try {
                        service.disposeHIT(hitid);
                        break;
                    } catch (InternalServiceException ise) {
                      LOGGER.warn(format("{0} {1}", name, ise));
                      if (overTime(name, wait))
                        return;
                      chill(wait);
                      wait *= 2;
                    }
                }
            }
        }
    }

    private static void approveAssignments(List<String> assignmentids) {
        String msg = String.format("Attempting to approve %d assignments", assignmentids.size());
        String name = "approveAssignments";
        System.out.println(msg);
        LOGGER.info(msg);
        int waittime = 1;
        // call to the batch assignment approval method never terminated.
        // synch outside the foor loop so new things arent posted in the interim.
        synchronized (service) {
            for (String assignmentid : assignmentids) {
                while (true) {
                    try {
                        service.approveAssignment(assignmentid, "Thanks.");
                        System.out.println("Approved " + assignmentid);
                        LOGGER.info("Approved " + assignmentid);
                        break;
                    } catch (InternalServiceException ise) { 
                      LOGGER.warn(format("{0} {1}", name, ise));
                      if (overTime(name, waittime))
                        return;
                      chill(1);
                      waittime *= 2;
                    }
                }
            }
        }
    }

    /**
     * Expires a specific HIT.
     */
    public static void expireHIT(HIT hit) {
        String name = "expireHIT";
        while (true){
            synchronized (service) {
                try{
                    service.forceExpireHIT(hit.getHITId());
                    return;
                }catch(InternalServiceException ise){
                  LOGGER.warn(MessageFormat.format("{0} {1}", name, ise));
                  chill(1);
                }catch(ObjectDoesNotExistException odne) {
                  LOGGER.warn(MessageFormat.format("{0} {1}", name, odne));
                  return;
                }
            }
        }
    }

    public static void expireHITs(List<String> hitids) {
        String name = "expireHITs";
        synchronized (service) {
            for (String hitid : hitids) {
                while(true) {
                    try {
                        service.forceExpireHIT(hitid);
                        String msg = String.format("Expired hit %s", hitid);
                        LOGGER.info(msg);
                        System.out.println(msg);
                        break;
                    } catch (InternalServiceException ise) { 
                      LOGGER.warn(MessageFormat.format("{0} {1}", name, ise));
                      chill(1); 
                    }
                }
            }
        }
    }

    public static String getWebsiteURL() {
        String name = "getWebsiteURL";
        synchronized (service) {
            while(true) {
                try {
                    String websiteURL = service.getWebsiteURL();
                    return websiteURL;
                } catch (InternalServiceException ise) {
                    LOGGER.warn(MessageFormat.format("{0} {1}", name, ise));
                  chill(3); 
                }
            }
        }
    }

    protected static QualificationRequirement answerOnce(Record record){
        assert(record!=null);
        assert(record.qualificationType!=null);
        return new QualificationRequirement(
                  record.qualificationType.getQualificationTypeId()
                , Comparator.NotEqualTo
                , 1
                , null
                , false
        );
    }

    public static String registerNewHitType(Record record) {
        String name = "registerNewHitType";
        int waittime = 1;
        assert(service!=null);
        synchronized (service) {
            while(true) {
                try {
                    Properties props = record.library.props;
                    String keywords = props.getProperty("keywords");
                    String description = "Can only be paid for this survey once.";
                    QualificationType qualificationType = service.createQualificationType(
                            record.survey.sid+gensym.next()+MturkLibrary.TIME
                            , keywords
                            , description
                            , QualificationTypeStatus.Active
                            , new Long(Integer.MAX_VALUE)
                            , null //test
                            , null //answer key
                            , null //test duration
                            , true //autogranted
                            , 0 //integer autogranted (count of 0)
                        );
                    assert(qualificationType != null);
                    record.qualificationType = qualificationType;
                    QualificationRequirement qr = answerOnce(record);
                    String hitTypeId = service.registerHITType( maxAutoApproveDelay
                            , Long.parseLong(props.getProperty("assignmentduration"))
                            , Double.parseDouble((String) props.get("reward"))
                            , props.getProperty("title")
                            , props.getProperty("keywords")
                            , props.getProperty("description")
                            , new QualificationRequirement[]{ qr }
                    );
                    record.hitTypeId = hitTypeId;
                    LOGGER.info(String.format("Qualification id: (%s)", qualificationType.getQualificationTypeId()));
                    return hitTypeId;
                } catch (InternalServiceException ise) {
                    LOGGER.warn(MessageFormat.format("{0} {1}", name, ise));
                    if (overTime(name, waittime))
                      throw new RuntimeException("FATAL - CANNOT REGISTER HIT TYPE");
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }

    public static String createHIT(String title, String description, String keywords, String xml, double reward, long assignmentDuration, long maxAutoApproveDelay, long lifetime, QualificationRequirement qr, String hitTypeId)
            throws ParseException {
        System.out.println(getWebsiteURL());
        String name = "createHIT";
        int waittime = 1;
        synchronized (service) {
            while(true) {
                try {
                    HIT hitid = service.createHIT(hitTypeId
                            , title
                            , description
                            , keywords
                            , xml
                            , reward
                            , assignmentDuration
                            , maxAutoApproveDelay
                            , lifetime
                            , 1
                            , ""
                            , new QualificationRequirement[]{qr}
                            , null
                        );
                    return hitid.getHITId();
                } catch (InternalServiceException ise) {
                    LOGGER.info(MessageFormat.format("{0} {1}", name, ise));
                    if (overTime(name, waittime))
                      throw new RuntimeException("FATAL - CANNOT CREATE HIT");
                    chill(waittime);
                    waittime *= 2;
                } catch (ObjectAlreadyExistsException e) {
                    LOGGER.info(MessageFormat.format("{0} {1}", name, e));
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }

    public static void removeQualification(Record record) {
        String name = "removeQualification";
        int waittime = 1;
        String qualid = record.qualificationType.getQualificationTypeId();
        LOGGER.info(String.format("Retiring qualification type : (%s)", qualid));
        synchronized (service) {
            while(true) {
                try {
                    service.updateQualificationType(qualid, "retiring", QualificationTypeStatus.Inactive);
                    record.qualificationType.setQualificationTypeStatus(QualificationTypeStatus.Inactive);
                    break;
                } catch (ObjectDoesNotExistException q) {
                    LOGGER.info(String.format("Qualification %s already removed", qualid));
                } catch (InternalServiceException ise) {
                    LOGGER.info(MessageFormat.format("{0} {1}", name, ise));
                    if (overTime(name, waittime)) {
                      LOGGER.warn(String.format("Cannot update qualification %s to inactive. Aborting.", qualid));
                      break;
                    }
                    chill(waittime);
                    waittime *= 2;
                }
            }
            while(true) {
                try {
                    service.disposeQualificationType(qualid);
                    break;
                } catch (InternalServiceException ise) {
                    LOGGER.info(MessageFormat.format("{0} {1}", name, ise));
                    if (overTime(name, waittime)) {
                        LOGGER.warn(String.format("Cannot dispose qualification %s. Aborting.", qualid));
                        break;
                    }
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }

    //***********************************************************//

    /**
     * Returns a copy of the Record {@link Record} of results for argument survey. This method synchronizes on manager,
     * so the current contents of the Record for this survey may be stale. If there is no Record recorded yet, the
     * method returns null.
     *
     * @param survey {@link Survey}
     * @return a copy of the Record {@link Record} associated with this Survey {@link Survey}.
     * @throws IOException
     */
    public static Record getRecord(Survey survey) 
            throws IOException, SurveyException {
        synchronized (manager) {
            Record r = manager.get(survey.sid);
            return r;
        }
    }

    public static void addRecord(Record record) {
        synchronized (manager) {
            manager.put(record.survey.sid, record);
            if (record.hitTypeId==null || record.qualificationType==null)
                registerNewHitType(record);
        }
    }

    /**
     * Given a Record {@link Record}, this method loops through the HITs {@link HIT} registered for the Record {@link Record}
     * and returns a list of HITs {@link HIT}. Note that if the argument is generated using getRecord, the resulting
     * list of HITs may be stale. This is generally fine for most operations in SurveyMan, but if the list must be as
     * fresh as possible, synchronize on manager and get the record that way.
     *
     * @param r {@link Record}
     * @return a list of HITs associated with this Record (i.e. the value associated with a given Survey {@link Survey}
     *  in manager.
     */
    public static List<HIT> listAvailableHITsForRecord (Record r) {
        List<HIT> hits = Arrays.asList(r.getAllHITs());
        ArrayList<HIT> retval = new ArrayList<HIT>();
        for (HIT hit : hits) {
            HIT thishit = getHIT(hit.getHITId());
            if (thishit.getHITStatus().equals(HITStatus.Assignable))
                retval.add(hit);
        }
       return retval;
    }

    private static SurveyResponse parseResponse(Assignment assignment, Survey survey)
            throws SurveyException, IOException {
        Record record = ResponseManager.getRecord(survey);
        return new SurveyResponse(survey, assignment, record);
    }

    protected static int addResponses(Survey survey, HIT hit)
            throws SurveyException, IOException {
        boolean success = false;
        Record r = manager.get(survey.sid);
        // references to things in the record
        List<SurveyResponse> responses = r.responses;
        List<SurveyResponse> botResponses = r.botResponses;
        QC qc = r.qc;
        // local vars
        List<SurveyResponse> validResponsesToAdd = new ArrayList<SurveyResponse>();
        List<SurveyResponse> randomResponsesToAdd = new ArrayList<SurveyResponse>();

        while (!success) {
            try{
                List<Assignment> assignments = getAllAssignmentsForHIT(hit);
                for (Assignment a : assignments) {
                    if (a.getAssignmentStatus().equals(AssignmentStatus.Submitted)) {
                        SurveyResponse sr = parseResponse(a, survey);
                        if (QCAction.addAsValidResponse(qc.assess(sr), a, r, sr))
                            validResponsesToAdd.add(sr);
                        else randomResponsesToAdd.add(sr);
                    }
                }
                responses.addAll(validResponsesToAdd);
                botResponses.addAll(randomResponsesToAdd);
                success=true;
            } catch (ServiceException se) {
                LOGGER.warn("ServiceException in addResponses "+se);
            }
        }
        return validResponsesToAdd.size();
    }

    /**
     * Tries to parse all of the Approved assignments into a SurveyResponse {@link SurveyResponse} list according to
     * some date window.
     *
     * @param survey {@link Survey}
     * @return a list of survey responses
     */
    public static List<SurveyResponse> getOldResponsesByDate(Survey survey, Calendar from, Calendar to) 
            throws SurveyException, IOException {
        List<SurveyResponse> responses = new ArrayList<SurveyResponse>();
        for (HIT hit : searchAllHITs())
            if (hit.getCreationTime().after(from) && hit.getCreationTime().before(to))
                for (Assignment assignment : getAllAssignmentsForHIT(hit))
                    if (assignment.getAssignmentStatus().equals(AssignmentStatus.Approved))
                        responses.add(parseResponse(assignment, survey));
        return responses;
    }

    /**
     * For a specific HIT {@link  HIT} Id, this function will extent the HIT's lifetime by the same length
     * as the original setting. The original setting is provided in the params argument.
     * Both arguments are typically extracted from a Record {@link Record} object associated with a
     * particular Survey {@link Survey} object.
     *
     * @param hitId {@link String}
     * @param params {@link Properties}
     */
    public static boolean renewIfExpired(String hitId, Properties params) {
        HIT hit = getHIT(hitId);
        if (hit.getExpiration().before(Calendar.getInstance())) {
            long extension = Long.valueOf(params.getProperty("hitlifetime"));
            extendHIT(hitId, 1, extension>=60?extension:60);
            return true;
        } else return false;
    }

    private static List<HIT> hitTask(HITStatus inputStatus) {
        List<HIT> hits = new ArrayList<HIT>();
        HIT[] hitarray = searchAllHITs();
        for (HIT hit : hitarray){
            HITStatus status = hit.getHITStatus();
            if (status.equals(inputStatus))
                hits.add(hit);
        }
        return hits;
    }

    /**
     * Returns a list of all currently unassignable HITs {@link HIT}. Note that this may include previously
     * unassigned HITs from old sessions (that is, the HITs may not be linked to the Surveys currently held
     * in manager)
     * @return A list of unassignable HITs.
     */
    public static List<HIT> unassignableHITs() {
        return hitTask(HITStatus.Unassignable);
    }

    /**
     * Deletes all expired HITs {@link HIT}. Also approves any pending assignments.
     * @return  A list of the expired HITs.
     */
    public static List<HIT> deleteExpiredHITs() {
        List<HIT> hits = new ArrayList<HIT>();
        List<String> assignments = new ArrayList<String>();
        List<String> hitids = new ArrayList<String>();
        List<HIT> tasks = hitTask(HITStatus.Reviewable);
        tasks.addAll(hitTask(HITStatus.Reviewing));
        for (HIT hit : tasks)
            if (hit.getExpiration().getTimeInMillis() < Calendar.getInstance().getTimeInMillis()){
                hits.add(hit);
                hitids.add(hit.getHITId());
            }
        for (HIT hit : hits) {
            List<Assignment> assignmentsForHIT = getAllAssignmentsForHIT(hit);
            for (Assignment a : assignmentsForHIT)
                if (a.getAssignmentStatus().equals(AssignmentStatus.Submitted))
                    assignments.add(a.getAssignmentId());
        }
        approveAssignments(assignments);
        deleteHITs(hitids);
        System.out.println(String.format("Deleted %d HITs", hitids.size()));
        return hits;
    }

    /**
     * Gets a list of all the assignable HITs currently listed on Mechanical Turk. Note that this may
     * include previously assigned HITs from old sessions (that is, the HITs may not be linked to the
     * Surveys {@link Survey} currently held in manager)
     * @return A list of assignable HITs.
     */
    public static List<HIT> assignableHITs() {
        return hitTask(HITStatus.Assignable);
    }



    /**
     * Expires all HITs that are not listed as Reviewable or Reviewing. Reviewable assignmenst are those for
     * which a worker has submitted a response. Reviewing is a special state that corresponds to a staging
     * area where jobs wait for review.
     * @return A list of HITs that have been expired.
     */
    public static List<HIT> expireOldHITs() {
        List<String> expiredHITIds = new LinkedList<String>();
        List<HIT> expiredHITs = new LinkedList<HIT>();
        HIT[] hitarray = searchAllHITs();
        for (HIT hit : hitarray){
            HITStatus status = hit.getHITStatus();
            if (! (status.equals(HITStatus.Reviewable) || status.equals(HITStatus.Reviewing))) {
                expiredHITs.add(hit);
                expiredHITIds.add(hit.getHITId());
            }
        }
        expireHITs(expiredHITIds);
        String msg = String.format("Expired %d HITs", expiredHITs.size());
        LOGGER.info(msg);
        System.out.println(msg);
        return expiredHITs;
    }

    public static void approveAllHITs(){
        HIT[] hits = searchAllHITs();
        List<String> assignmentidlist = new LinkedList<String>();
        for (HIT hit : hits){
            List<Assignment> assignments = getAllAssignmentsForHIT(hit);
            for (Assignment assignment : assignments)
                if (assignment.getAssignmentStatus().equals(AssignmentStatus.Submitted))
                    assignmentidlist.add(assignment.getAssignmentId());
        }
        String msg1 = String.format("Attempting to approve %d assignments", assignmentidlist.size());
        System.out.println(msg1);
        approveAssignments(assignmentidlist);
        String msg2 = String.format("Approved %d assignments.", assignmentidlist.size());
        System.out.println(msg2);
        LOGGER.info(msg1 + "\n" + msg2);
    }

    public static void freshenQualification(Record record) {
        int waittime = 1;
        String name = "freshenQualification";
        synchronized (service) {
            while(true) {
                try{
                    if (record.qualificationType.getQualificationTypeStatus().equals(QualificationTypeStatus.Inactive)) {
                        service.updateQualificationType(record.qualificationType.getQualificationTypeId(), "reusing", QualificationTypeStatus.Active);
                        record.qualificationType.setQualificationTypeStatus(QualificationTypeStatus.Active);
                        return;
                    }
                }catch(InternalServiceException ise) {
                    if (overTime(name, waittime)) {
                        LOGGER.warn(String.format("Cannot freshing qualification %s to active.", record.qualificationType.getQualificationTypeId()));
                        break;
                    }
                    LOGGER.warn(ise);
                    chill(waittime);
                    waittime *= 2;
                }
            }
        }
    }

    public static void main(String[] args)
            throws IOException, SurveyException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (args.length < 4) {
            System.err.println("Usage :\n" +
                    "\tjava -cp path/to/surveyman.jar system.mturk.ResponseManager <fromDate> <toDate> <filename> <sep>\n" +
                    "\twhere\n" +
                    "\t<fromDate>, <toDate>\tare dates formatted as YYYYMMDD (e.g. Jan 1, 2013 would be 20130101)\n" +
                    "\t<filename>\t\t\t\tis the (relative or absolute) path to the file of interest\n" +
                    "\t<sep>\t\t\t\t\tis the field separator\n");
        } else {
            Calendar from = Calendar.getInstance();
            from.set(Integer.parseInt(args[0].substring(0,4)), Integer.parseInt(args[0].substring(4,6)), Integer.parseInt(args[0].substring(6,8)));
            System.out.println("From Date:"+new SimpleDateFormat().format(from.getTime(), new StringBuffer(), new FieldPosition(DateFormat.DATE_FIELD)));
            Calendar to = Calendar.getInstance();
            to.set(Integer.parseInt(args[1].substring(0,4)), Integer.parseInt(args[1].substring(4,6)), Integer.parseInt(args[1].substring(6,8)));
            System.out.println("To Date:"+new SimpleDateFormat().format(to.getTime(), new StringBuffer(), new FieldPosition(DateFormat.DATE_FIELD)));
            CSVParser parser = new CSVParser(new CSVLexer(args[2], args[3]));
            Survey survey = parser.parse();
            List<SurveyResponse> responses = getOldResponsesByDate(survey, from, to);
            MturkLibrary library = new MturkLibrary();
            Record record = new Record(survey);
            record.responses = responses;
            Runner.writeResponses(survey, record);
            System.out.println(String.format("Response can be found in %s", record.outputFileName));
        }
    }
}
