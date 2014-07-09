/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa HealthCare.
 * Portions created by the Initial Developer are Copyright (C) 2006-2008
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See listed authors below.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.dcm4chex.archive.common;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since May 8, 2009
 */
public class PatientMatching implements Serializable{

    private static final long serialVersionUID = -5066423063497788483L;

    private static final String PID = "pid";
    private static final String ISSUER = "issuer";
    private static final String FAMILYNAME = "familyname";
    private static final String GIVENNAME = "givenname";
    private static final String MIDDLENAME = "middlename";
    private static final String NAMEPREFIX = "nameprefix";
    private static final String NAMESUFFIX = "namesuffix";
    private static final String BIRTHDATE = "birthdate";
    private static final String SEX = "sex";
    private static final String INITIAL = "(1)";
    private static final String IGNORE = "ignore";

    private final boolean trustPatientIDWithIssuer;
    private final boolean unknownPatientIDAlwaysMatch;
    private final boolean unknownIssuerAlwaysMatch;
    private final boolean familyNameMustMatch;
    private final boolean familyNameInitialMatch;
    private final boolean unknownFamilyNameAlwaysMatch;
    private final boolean givenNameMustMatch;
    private final boolean givenNameInitialMatch;
    private final boolean unknownGivenNameAlwaysMatch;
    private final boolean middleNameMustMatch;
    private final boolean middleNameInitialMatch;
    private final boolean unknownMiddleNameAlwaysMatch;
    private final boolean namePrefixMustMatch;
    private final boolean namePrefixInitialMatch;
    private final boolean unknownNamePrefixAlwaysMatch;
    private final boolean nameSuffixMustMatch;
    private final boolean nameSuffixInitialMatch;
    private final boolean unknownNameSuffixAlwaysMatch;
    private final boolean birthDateMustMatch;
    private final boolean unknownBirthDateAlwaysMatch;
    private final boolean sexMustMatch;
    private final boolean unknownSexAlwaysMatch;
    private final Pattern ignore;
    private PatientMatching altDemographicsMatch;
    
    public PatientMatching(String s) {
        this(indexOf(s, PID), indexOf(s, ISSUER), s);
    }

    private PatientMatching(int pid, int issuer, String s) {
        boolean trust = s.charAt(s.length()-1) == ']';
        int alt = indexOfAlt(s);
        if (alt != -1) {
            altDemographicsMatch = 
                    new PatientMatching(pid, issuer, s.substring(alt+1));
            s = s.substring(0, alt);
        }
        int familyName = indexOf(s, FAMILYNAME);
        int givenName = indexOf(s, GIVENNAME);
        int middleName = indexOf(s, MIDDLENAME);
        int namePrefix = indexOf(s, NAMEPREFIX);
        int nameSuffix = indexOf(s, NAMESUFFIX);
        int birthdate = indexOf(s, BIRTHDATE);
        int sex = indexOf(s, SEX);
        if (pid == -1 || issuer == -1
                || initialMatch(s, pid, PID)
                || initialMatch(s, issuer, ISSUER)
                || initialMatch(s, birthdate, BIRTHDATE)
                || initialMatch(s, sex, SEX)) {
            throw new IllegalArgumentException(s);
        }
        familyNameMustMatch = familyName != -1;
        givenNameMustMatch = givenName != -1;
        middleNameMustMatch = middleName != -1;
        namePrefixMustMatch = namePrefix != -1;
        nameSuffixMustMatch = nameSuffix != -1;
        birthDateMustMatch = birthdate != -1;
        sexMustMatch = sex != -1;
        unknownPatientIDAlwaysMatch = unknownAlwaysMatch(s, pid, PID, false);
        unknownIssuerAlwaysMatch = unknownAlwaysMatch(s, issuer, ISSUER, false);
        familyNameInitialMatch = initialMatch(s, familyName, FAMILYNAME);
        unknownFamilyNameAlwaysMatch = 
                unknownAlwaysMatch(s, familyName, FAMILYNAME, familyNameInitialMatch);
        givenNameInitialMatch = initialMatch(s, givenName, GIVENNAME);
        unknownGivenNameAlwaysMatch =
                unknownAlwaysMatch(s, givenName, GIVENNAME, givenNameInitialMatch);
        middleNameInitialMatch = initialMatch(s, middleName, MIDDLENAME);
        unknownMiddleNameAlwaysMatch =
                unknownAlwaysMatch(s, middleName, MIDDLENAME, middleNameInitialMatch);
        namePrefixInitialMatch = initialMatch(s, namePrefix, NAMEPREFIX);
        unknownNamePrefixAlwaysMatch =
                unknownAlwaysMatch(s, namePrefix, NAMEPREFIX, namePrefixInitialMatch);
        nameSuffixInitialMatch = initialMatch(s, nameSuffix, NAMESUFFIX);
        unknownNameSuffixAlwaysMatch =
                unknownAlwaysMatch(s, nameSuffix, NAMESUFFIX, nameSuffixInitialMatch);
        unknownBirthDateAlwaysMatch =
                unknownAlwaysMatch(s, birthdate, BIRTHDATE, false);
        unknownSexAlwaysMatch =
                unknownAlwaysMatch(s, sex, SEX, false);
        ignore = ignorePattern(s);
        trustPatientIDWithIssuer = trust
                || !familyNameMustMatch
                && !givenNameMustMatch && !middleNameMustMatch
                && !namePrefixMustMatch && !nameSuffixMustMatch
                && !birthDateMustMatch && !sexMustMatch;
        if (unknownPatientIDAlwaysMatch && !familyNameMustMatch) {
            throw new IllegalArgumentException(s);
        }
    }

    private int indexOfAlt(String s) {
        int ignore = s.indexOf(IGNORE);
        int alt = -1;
        while ((alt = s.indexOf('|', alt+1)) != -1)
            // check if '|' not part of ignore("<regex>")
            if (ignore == -1 || ignore > alt
                    || s.indexOf("\")", ignore+2) < alt)
                return alt;
        return -1;
    }

    public final boolean isTrustPatientIDWithIssuer() {
        return trustPatientIDWithIssuer;
    }

    public boolean isUnknownIssuerAlwaysMatch() {
        return unknownIssuerAlwaysMatch;
    }

    public final PatientMatching getAltDemographicsMatch() {
        return altDemographicsMatch;
    }

    public final void setAltDemographicsMatch(PatientMatching altDemographicsMatch) {
        this.altDemographicsMatch = altDemographicsMatch;
    }

    private boolean isUnknownPersonNameAlwaysMatch() {
        return unknownFamilyNameAlwaysMatch && unknownGivenNameAlwaysMatch
                && unknownMiddleNameAlwaysMatch && unknownNamePrefixAlwaysMatch
                && unknownNameSuffixAlwaysMatch;
    }

    private boolean initialMatch(String s, int index, String substr) {
        return index != -1 && s.startsWith(INITIAL, index + substr.length());
    }

    private boolean unknownAlwaysMatch(String s, int index, String substr,
            boolean initialMatch) {
        if (index == -1)
            return true;
        int after = index + substr.length();
        if (initialMatch)
            after += INITIAL.length();
        return after < s.length() && s.charAt(after) == '?';
    }

    private Pattern ignorePattern(String s) {
        int index = s.indexOf(IGNORE);
        if (index == -1)
            return null;
        int after = index + IGNORE.length();
        int begin = after+2;
        int end;
        if (!s.startsWith("(\"", after)
                || (end = s.indexOf("\")", begin)) == -1)
                throw new IllegalArgumentException(s);
        String regex = s.substring(begin, end);
        return Pattern.compile(regex);
    }

    private static int indexOf(String str, String substr) {
        int index = str.indexOf(substr);
        if (index != -1) {
            int after;
            if (index > 0 
                    && " ,[".indexOf(str.charAt(index-1)) == -1
                    || (after = index + substr.length()) < str.length() 
                    && " ,]?(".indexOf(str.charAt(after)) == -1) {
                throw new IllegalArgumentException(str);
            }
        }
        return index;
    }

    public String toString() {
        return toStringBuilder(new StringBuilder()).toString();
    }

    public StringBuilder toStringBuilder(StringBuilder sb) {
        sb.append(PID);
        if (unknownPatientIDAlwaysMatch) {
            sb.append('?');
        }
        sb.append(',').append(ISSUER);
        if (unknownIssuerAlwaysMatch) {
            sb.append('?');
        }
        if (familyNameMustMatch || givenNameMustMatch || middleNameMustMatch
                || namePrefixMustMatch || nameSuffixMustMatch 
                || birthDateMustMatch || sexMustMatch) {
            sb.append(',');
            if (trustPatientIDWithIssuer) {
                sb.append('[');
            }
            appendDemographicsMatch(sb);
            for (PatientMatching m = altDemographicsMatch; m != null;
                    m = m.altDemographicsMatch)
                m.appendDemographicsMatch(sb.append('|'));
            if (trustPatientIDWithIssuer) {
                sb.append(']');
            }
        }
        return sb;
    }

    private void appendDemographicsMatch(StringBuilder sb) {
        int count = 0;
        if (ignore != null) {
            count++;
            sb.append(IGNORE).append("(\"").append(ignore).append("\")");
        }
        if (familyNameMustMatch) {
            if (count++ > 0) {
                sb.append(',');
            }
            sb.append(FAMILYNAME);
            if (familyNameInitialMatch) {
                sb.append(INITIAL);
            }
            if (unknownFamilyNameAlwaysMatch) {
                sb.append('?');
            }
        }
        if (givenNameMustMatch) {
            if (count++ > 0) {
                sb.append(',');
            }
            sb.append(GIVENNAME);
            if (givenNameInitialMatch) {
                sb.append(INITIAL);
            }
            if (unknownGivenNameAlwaysMatch) {
                sb.append('?');
            }
        }
        if (middleNameMustMatch) {
            if (count++ > 0) {
                sb.append(',');
            }
            sb.append(MIDDLENAME);
            if (middleNameInitialMatch) {
                sb.append(INITIAL);
            }
            if (unknownMiddleNameAlwaysMatch) {
                sb.append('?');
            }
        }
        if (namePrefixMustMatch) {
            if (count++ > 0) {
                sb.append(',');
            }
            sb.append(NAMEPREFIX);
            if (namePrefixInitialMatch) {
                sb.append(INITIAL);
            }
            if (unknownNamePrefixAlwaysMatch) {
                sb.append('?');
            }
        }
        if (nameSuffixMustMatch) {
            if (count++ > 0) {
                sb.append(',');
            }
            sb.append(NAMESUFFIX);
            if (nameSuffixInitialMatch) {
                sb.append(INITIAL);
            }
            if (unknownNameSuffixAlwaysMatch) {
                sb.append('?');
            }
        }
        if (birthDateMustMatch) {
            if (count > 0) {
                sb.append(',');
            }
            sb.append(BIRTHDATE);
            if (unknownBirthDateAlwaysMatch) {
                sb.append('?');
            }
        }
        if (sexMustMatch) {
            if (count > 0) {
                sb.append(',');
            }
            sb.append(SEX);
            if (unknownSexAlwaysMatch) {
                sb.append('?');
            }
        }
    }

    public List<Pattern> compilePNPatterns(String familyName, String givenName,
            String middleName, String namePrefix, String nameSuffix) {
        List<Pattern> list = new LinkedList<Pattern>();
        for (PatientMatching m = this; m != null; m = m.altDemographicsMatch )
            list.add(m.compilePNPattern(familyName, givenName, middleName,
                    namePrefix, nameSuffix));
        return list ;
    }

    public Pattern compilePNPattern(String familyName, String givenName,
            String middleName, String namePrefix, String nameSuffix) {
        if (allMatchesFor(familyName, givenName, middleName, namePrefix,
                nameSuffix)) {
            return null;
        }
        boolean appendNameSuffix =
                nameSuffixMustMatch && nameSuffix != null;
        boolean appendNamePrefix = appendNameSuffix
                || namePrefixMustMatch && namePrefix != null;
        boolean appendMiddleName = appendNamePrefix
                || middleNameMustMatch && middleName != null;
        boolean appendGivenName = appendMiddleName
                || givenNameMustMatch && givenName != null;
        boolean appendFamilyName = appendGivenName
                || familyNameMustMatch && familyName != null;
        StringBuilder regex = new StringBuilder();
        if (appendFamilyName) {
            appendRegex(regex, ignore(familyName), familyNameMustMatch,
                    familyNameInitialMatch, unknownFamilyNameAlwaysMatch);
            regex.append("\\^");
            if (appendGivenName) {
                appendRegex(regex, ignore(givenName), givenNameMustMatch,
                        givenNameInitialMatch, unknownGivenNameAlwaysMatch);
                regex.append("\\^");
                if (appendMiddleName) {
                    appendRegex(regex, ignore(middleName), middleNameMustMatch,
                            middleNameInitialMatch, unknownMiddleNameAlwaysMatch);
                    regex.append("\\^");
                    if (appendNamePrefix) {
                        appendRegex(regex, ignore(namePrefix), namePrefixMustMatch,
                                namePrefixInitialMatch, unknownNamePrefixAlwaysMatch);
                        regex.append("\\^");
                        if (appendNameSuffix) {
                            appendRegex(regex, ignore(nameSuffix), nameSuffixMustMatch,
                                    nameSuffixInitialMatch, unknownNameSuffixAlwaysMatch);
                        }
                    }
                }
            }
        }
        if (!appendNameSuffix)
            regex.append(".*");
        return Pattern.compile(regex.toString());
    }

    public String ignore(String s) {
        return (ignore == null ||  s == null) ? s
                : ignore.matcher(s).replaceAll("");
    }

    private static void appendRegex(StringBuilder regex, String value,
            boolean mustMatch, boolean initialMatch,
            boolean unknownAlwaysMatch) {
        if (!mustMatch || value == null || value.length() == 0) {
            regex.append("[^\\^]*");
        } else if (initialMatch) {
            regex.append(unknownAlwaysMatch ? "(\\Q" : "\\Q")
                .append(value.charAt(0))
                .append(unknownAlwaysMatch ? "\\E[^\\^]*)?" : "\\E[^\\^]*");
        } else {
            regex.append(unknownAlwaysMatch ? "(\\Q" : "\\Q")
                 .append(value)
                 .append(unknownAlwaysMatch ? "\\E)?" : "\\E");
        }
    }

    public boolean noMatchesFor(String pid, String issuer, String familyName,
            String givenName, String middleName, String namePrefix,
            String nameSuffix, String birthdate, String sex) {
        return !unknownPatientIDAlwaysMatch && pid == null
                || !unknownIssuerAlwaysMatch && issuer == null
                || !(trustPatientIDWithIssuer && pid != null && issuer != null)
                && noMatchesFor(familyName, givenName, middleName, namePrefix,
                        nameSuffix, birthdate, sex);
    }

    public boolean noMatchesFor(String familyName, String givenName,
            String middleName, String namePrefix, String nameSuffix,
            String birthdate, String sex) {
        return (!unknownFamilyNameAlwaysMatch && familyName == null
                || !unknownGivenNameAlwaysMatch && givenName == null
                || !unknownMiddleNameAlwaysMatch && middleName == null
                || !unknownNamePrefixAlwaysMatch && namePrefix == null
                || !unknownNameSuffixAlwaysMatch && nameSuffix == null
                || !unknownBirthDateAlwaysMatch && birthdate == null
                || !unknownSexAlwaysMatch && sex == null)
                && (altDemographicsMatch == null 
                || altDemographicsMatch.noMatchesFor(familyName, givenName,
                        middleName, namePrefix, nameSuffix, birthdate, sex));
    }

    private boolean allMatchesFor(String familyName, String givenName,
            String middleName, String namePrefix, String nameSuffix) {
        return (!familyNameMustMatch 
                        || unknownFamilyNameAlwaysMatch && familyName == null)
            && (!givenNameMustMatch
                        || unknownGivenNameAlwaysMatch && givenName == null)
            && (!middleNameMustMatch
                        || unknownMiddleNameAlwaysMatch && middleName == null)
            && (!namePrefixMustMatch
                        || unknownNamePrefixAlwaysMatch && namePrefix == null)
            && (!nameSuffixMustMatch
                        || unknownNameSuffixAlwaysMatch && nameSuffix == null);
    }

    public boolean allMatchesFor(String familyName, String givenName,
            String middleName, String namePrefix, String nameSuffix,
            String birthdate, String sex) {
        return allMatchesFor(familyName, givenName, middleName, namePrefix, nameSuffix)
                && (!birthDateMustMatch
                        || unknownBirthDateAlwaysMatch && birthdate == null)
                && (!sexMustMatch
                        || unknownSexAlwaysMatch && sex == null)
                || altDemographicsMatch != null 
                && altDemographicsMatch.allMatchesFor(familyName, givenName,
                        middleName, namePrefix, nameSuffix, birthdate, sex);
    }

    public boolean matches(String pn, String birthdate, String sex,
            Iterator<Pattern> pnPatternIter, String birthdate2, String sex2) {
        Pattern pnPattern = pnPatternIter.next();
        return (pnPattern == null 
                        || (pn == null 
                                ? isUnknownPersonNameAlwaysMatch()
                                : pnPattern.matcher(ignore(pn)).matches()))
                && (!birthDateMustMatch 
                        || (birthdate == null || birthdate2 == null)
                                ? unknownBirthDateAlwaysMatch
                                : birthdate.equals(birthdate2))
                && (!sexMustMatch 
                        || ((sex == null || sex2 == null)
                                ? unknownSexAlwaysMatch
                                : sex.equals(sex2)))
                || altDemographicsMatch != null
                && altDemographicsMatch.matches(pn, birthdate, sex,
                        pnPatternIter, birthdate2, sex2);
    }
    
    public static void main(String[] args) {
        for (String s : args) {
            System.out.println(s);
            System.out.println(new PatientMatching(s));
            System.out.println();
        }
    }
}
