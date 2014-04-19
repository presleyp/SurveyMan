package system.mturk.generators;

import system.generators.HTML;
import system.mturk.MturkLibrary;
import system.Slurpie;
import java.io.*;

import survey.Survey;
import survey.SurveyException;

public class XML {

    public static final int maxQuestionXMLLength = 131072;
    
    public static String getXMLString(Survey survey) throws SurveyException {
        String retval;
        try {
            retval = String.format(Slurpie.slurp(MturkLibrary.XMLSKELETON), HTML.getHTMLString(survey, new system.mturk.generators.HTML()));
            if (retval.length() > maxQuestionXMLLength)
                throw new MaxXMLLengthException(retval.length());
        } catch (FileNotFoundException e1) {
            throw new SurveyException(e1.getMessage()){};
        } catch (IOException e2) {
            throw new SurveyException(e2.getMessage()){};
        }
        return retval;
    }


    public static class MaxXMLLengthException extends SurveyException{
        public MaxXMLLengthException(int stringLength){
            super(String.format("Question length is %d bytes, exceeds max length of %d bytes by %d bytes."
                    , stringLength
                    , XML.maxQuestionXMLLength
                    , stringLength - XML.maxQuestionXMLLength));
        }
    }

}