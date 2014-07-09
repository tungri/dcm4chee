package org.dcm4chex.archive.common;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import junit.framework.TestCase;

public class PatientMatchingTest extends TestCase {

	public void test_ctor_shouldThrow_whenPidIsMissing() {
		Exception e = ExceptionTrap.capture(new ExceptionTrigger() {
			public void trip() {
				String s = "issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
				new PatientMatching(s);
			}
		});
		assertEquals("Unexpected exception encountered", IllegalArgumentException.class, e.getClass());
	}
	
	public void test_ctor_shouldThrow_whenIssuerIsMissing() {
		Exception e = ExceptionTrap.capture(new ExceptionTrigger() {
			public void trip() {
				String s = "pid,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
				new PatientMatching(s);
			}
		});
		assertEquals("Unexpected exception encountered", IllegalArgumentException.class, e.getClass());
	}
	
	public void test_ctor_shouldThrow_whenPidHasInitialMatch() {
		Exception e = ExceptionTrap.capture(new ExceptionTrigger() {
			public void trip() {
				String s = "pid(1),issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
				new PatientMatching(s);
			}
		});
		assertEquals("Unexpected exception encountered", IllegalArgumentException.class, e.getClass());
	}
	
	public void test_ctor_shouldThrow_whenIssuerHasInitialMatch() {
		Exception e = ExceptionTrap.capture(new ExceptionTrigger() {
			public void trip() {
				String s = "pid,issuer(1),[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
				new PatientMatching(s);
			}
		});
		assertEquals("Unexpected exception encountered", IllegalArgumentException.class, e.getClass());
	}
	
	public void test_ctor_shouldThrow_whenBirthdateHasInitialMatch() {
		Exception e = ExceptionTrap.capture(new ExceptionTrigger() {
			public void trip() {
				String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate(1),sex]";
				new PatientMatching(s);
			}
		});
		assertEquals("Unexpected exception encountered", IllegalArgumentException.class, e.getClass());
	}
	
	public void test_ctor_shouldThrow_whenSexHasInitialMatch() {
		Exception e = ExceptionTrap.capture(new ExceptionTrigger() {
			public void trip() {
				String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex(1)]";
				new PatientMatching(s);
			}
		});
		assertEquals("Unexpected exception encountered", IllegalArgumentException.class, e.getClass());
	}

	public void test_ctor_shouldSetUnknownIssuerAlwaysMatch_whenUnknownMatchSuffixIsPresent() {
		String s = "pid,issuer?,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("unknownIssuerAlwaysMatch should be set", pm.isUnknownIssuerAlwaysMatch());
	}
	
	public void test_ctor_shouldSetTrustPidWithIssuer_whenDemographicsAreNotPresent() {
		String s = "pid,issuer";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("trustPatientIDWithIssuer should be set", pm.isTrustPatientIDWithIssuer());
	}
	
	public void test_ctor_shouldSetTrustPidWithIssuer_whenDemographicsAreExcluded() {
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("trustPatientIDWithIssuer should be set", pm.isTrustPatientIDWithIssuer());
	}
	
	public void test_ctor_shouldThrow_whenPidAlwaysMatchAndFamilyNameNotPresent() {
		Exception e = ExceptionTrap.capture(new ExceptionTrigger() {
			public void trip() {
				String s = "pid?,issuer";
				new PatientMatching(s);
			}
		});
		assertEquals("Unexpected exception encountered", IllegalArgumentException.class, e.getClass());
	}
	
	public void test_compilePNPattern_shouldMatch_whenFullNameMatches() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String personName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern pattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + pattern.pattern() + " does not match " + personName, pattern.matcher(personName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenSuffixMatchesAny() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "";
		String personName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename,nameprefix,namesuffix?";
		PatientMatching pm = new PatientMatching(s);
		Pattern pattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + pattern.pattern() + " does not match " + personName, pattern.matcher(personName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasFamilyName_andExistingHasNone() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName("", givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname?,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasGivenName_andExistingHasNone() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, "", middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname?,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasMiddleName_andExistingHasNone() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, "", prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename?,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasPrefix_andExistingHasNone() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, "", suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename,nameprefix?,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasSuffix_andExistingHasNone() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, "");
		
		String s = "pid,issuer,familyname,givenname,middlename,nameprefix,namesuffix?";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasNone_andExistingHasFamilyName() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);

		String s = "pid,issuer,familyname?,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(null, givenName, middleName, prefix, suffix);

		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName,
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasNone_andExistingHasGivenname() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);

		String s = "pid,issuer,familyname,givenname?,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, null, middleName, prefix, suffix);

		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName,
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasNone_andExistingHasMiddleName() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);

		String s = "pid,issuer,familyname,givenname,middlename?,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, null, prefix, suffix);

		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName,
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasNone_andExistingHasPrefix() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);

		String s = "pid,issuer,familyname,givenname,middlename,nameprefix?,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, null, suffix);

		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName,
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasNone_andExistingHasSuffix() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);

		String s = "pid,issuer,familyname,givenname,middlename,nameprefix,namesuffix?";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, null);

		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName,
				incomingPatientPattern.matcher(existingPatientName).matches());
	}

	public void test_compilePNPattern_shouldMatch_whenIncomingHasFamilyNameCharactersToIgnore() {
		String familyName = "Van Der-Borough";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName("VanDerBorough", givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\" |-\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasFamilyNameCharactersToIgnore() {
		String familyName = "Van Der-Borough";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\" |-\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				"VanDerBorough", givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingAndIncomingHaveFamilyNameCharactersToIgnore() {
		String familyName = "Van Der-Borough";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\" |-|,\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				"Van-Der, Borough", givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasGivenNameCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "J-J";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, "JJ", middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\"-|\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasGivenNameCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "J-J";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\"-\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, "J-J", middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingAndIncomingHaveGivenNameCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "J.J";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\"-|\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, "J-J", middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasMiddleNameCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F.D.";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, "FD", prefix, suffix);
		
		String s = "pid,issuer,ignore(\"\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasMiddleNameCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F.D.";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\"\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, "FD", prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingAndIncomingHaveMiddleNameCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F.D.";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\" |-|\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, "F D-", prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasPrefixCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, "DR", suffix);
		
		String s = "pid,issuer,ignore(\"\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasPrefixCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\"\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, "DR", suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingAndIncomingHavePrefixCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\" |\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, "D R", suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasSuffixCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, "PHD");
		
		String s = "pid,issuer,ignore(\"\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);

		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasSuffixCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\"\\.\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, "PHD");
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingAndIncomingHaveSuffixCharactersToIgnore() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		String s = "pid,issuer,ignore(\"-|\\.|!\"),familyname,givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, "P-H-D!");
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasMultipartName_andIncomingHasMultipartName() {
		String familyName = "Smith";
		String givenName = "John JR";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,ignore(\" |JR|SR\"),familyname,givenname,middlename,nameprefix,namesuffix(1)";
		PatientMatching pm = new PatientMatching(s);
				
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, "John SR", middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_matcher_shouldMatch_whenIncomingHasDateThatMatchesExisting() {
		String familyName = "Smith";
		String givenName = "John JR";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthdate = "19730920";
		String sex = "M";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,birthdate";
		PatientMatching pm = new PatientMatching(s);
				
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, birthdate, sex, incomingPatientPatterns.iterator(),
						birthdate, sex));
	}
	
	public void test_matcher_shouldNotMatch_whenIncomingHasDateThatDoesNotMatchExisting() {
		String familyName = "Smith";
		String givenName = "John JR";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthdate = "19730920";
		String sex = "M";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,birthdate";
		PatientMatching pm = new PatientMatching(s);
				
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertFalse("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, birthdate, sex, incomingPatientPatterns.iterator(),
						"19730921", sex));
	}
	
	public void test_matcher_shouldMatch_whenIncomingHasSexThatMatchesExisting() {
		String familyName = "Smith";
		String givenName = "John JR";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthdate = "19730920";
		String sex = "M";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,birthdate,sex";
		PatientMatching pm = new PatientMatching(s);
				
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, birthdate, sex, incomingPatientPatterns.iterator(),
						birthdate, sex));
	}
	
	public void test_matcher_shouldNotMatch_whenIncomingHasSexThatDoesNotMatchExisting() {
		String familyName = "Smith";
		String givenName = "John JR";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthdate = "19730920";
		String sex = "M";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,birthdate,sex";
		PatientMatching pm = new PatientMatching(s);
				
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertFalse("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, birthdate, sex, incomingPatientPatterns.iterator(),
						birthdate, "F"));
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasFamilyName_andExistingHasInitial() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName("S", givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname(1),givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasGivenName_andExistingHasInitial() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, "J", middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname(1),middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenIncomingHasMiddleName_andExistingHasInitial() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, "F", prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename(1),nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasFamilyName_andIncomingHasInitial() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname(1),givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern("S", givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasGivenName_andIncomingHasInitial() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname(1),middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, "J", middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasMiddleName_andIncomingHasInitial() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename(1),nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, "F", prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasFamilyName_andIncomingHasSameFirstLetter_andInitialIsInExpression() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname(1),givenname,middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern("Smythe", givenName, middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasGivenName_andIncomingHasSameFirstLetter_andInitialIsInExpression() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname(1),middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, "Jack", middleName, prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasMiddleName_andIncomingHasSameFirstLetter_andInitialIsInExpression() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename(1),nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, "Frank", prefix, suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasPrefix_andIncomingHasSameFirstLetter_andInitialIsInExpression() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename,nameprefix(1),namesuffix";
		PatientMatching pm = new PatientMatching(s);
		Pattern incomingPatientPattern = pm.compilePNPattern(familyName, givenName, middleName, "D", suffix);
		
		assertTrue("Pattern " + incomingPatientPattern.pattern() + " does not match " + existingPatientName, 
				incomingPatientPattern.matcher(existingPatientName).matches());
	}
	
	public void test_compilePNPattern_shouldMatch_whenExistingHasSuffix_andIncomingHasSameFirstLetter_andInitialIsInExpression() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename,nameprefix,namesuffix(1)";
		PatientMatching pm = new PatientMatching(s);
				
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, "P.H.D");
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	public void test_compilePNPattern_shouldMatch_whenOneOfMulitpleExpressionsMatches() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "Frederick";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthdate = "";
		String sex = "M";
		String existingPatientName = makePersonName(familyName, givenName, middleName, prefix, suffix);
		
		String s = "pid,issuer,familyname,givenname,middlename,nameprefix,namesuffix" + 
			"|pid,issuer,familyname(1),givenname,middlename,nameprefix,namesuffix" +
			"|pid,issuer,familyname,givenname(1),middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, "Jack", middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, birthdate, sex, incomingPatientPatterns.iterator(),
						birthdate, sex));
	}
	
	public void test_compilePNPattern_shouldHandleRegexCreation_whenIgnoreCreatesAnEmptyString() {
		String existingPatientName = "PATTERSON^ JR^JUDE A^^";
		
		String familyName = "PATTERSON";
		String givenName = " JR";
		String middleName = "JUDE A";
		String prefix = null;
		String suffix = null;
		
		String s = "pid,issuer,ignore(\" |JR\"),familyname,givenname(1),middlename,nameprefix,namesuffix";
		PatientMatching pm = new PatientMatching(s);
		
		List<Pattern> incomingPatientPatterns = pm.compilePNPatterns(
				familyName, givenName, middleName, prefix, suffix);
		
		assertTrue("Matching pattern to " + existingPatientName + " not found in " +
				patternsListToString(incomingPatientPatterns), 
				pm.matches(existingPatientName, null, null, incomingPatientPatterns.iterator(),
						null, null));
	}
	
	private String patternsListToString(List<Pattern> incomingPatientPatterns) {
		StringBuilder sb = new StringBuilder();
		
		for (Iterator<Pattern> iterator = incomingPatientPatterns.iterator(); iterator.hasNext();) {
			Pattern pattern = iterator.next();
			if ( sb.length() > 0 ) {
				sb.append("|");
			}
			sb.append(pattern.pattern());
		}
		
		return sb.toString();
	}

	public void test_noMatchesFor_shouldReturnTrue_whenUnknownPIDNotSet_andPIDIsNull() {
		String pid = null;
		String issuer = "ISSUER";
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because a PID is required", 
				pm.noMatchesFor(pid, issuer, familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_noMatchesFor_shouldReturnTrue_whenUnknownIssuerNotSet_andIssuerIsNull() {
		String pid = "PID";
		String issuer = null;
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because an issuer is required", 
				pm.noMatchesFor(pid, issuer, familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_noMatchesFor_shouldReturnTrue_whenUnknownFamilyNameNotSet_andFamilyNameIsNull() {
		String familyName = null;
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because a familyname is required", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_noMatchesFor_shouldReturnTrue_whenUnknownGivenNameNotSet_andGivenNameIsNull() {
		String familyName = "Smith";
		String givenName = null;
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because a givenname is required", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_noMatchesFor_shouldReturnTrue_whenUnknownMiddleNameNotSet_andMiddleNameIsNull() {
		String familyName = "Scott";
		String givenName = "John";
		String middleName = null;
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because a middlename is required", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_noMatchesFor_shouldReturnTrue_whenUnknownPrefixNotSet_andPrefixIsNull() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = null;
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because a prefix is required", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_noMatchesFor_shouldReturnTrue_whenUnknownSuffixNotSet_andSuffixIsNull() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = null;
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because a suffix is required", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_noMatchesFor_shouldReturnTrue_whenUnknownBirthDateNotSet_andBirthDateIsNull() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = null;
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because a birthdate is required", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_noMatchesFor_shouldReturnTrue_whenUnknownSexNotSet_andSexIsNull() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = null;
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("No matches should be true because a sex is required", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	// this, along with the next test are sufficient because they are the opposite of the above
	// and are always the exact inverse
	public void test_noMatchesFor_shouldReturnFalse_whenUnknownPIDSet_andPIDIsNull() {
		String pid = null;
		String issuer = "ISSUER";
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid?,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("No matches should be false because a PID is not required", 
				pm.noMatchesFor(pid, issuer, familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	// this, along with the previous test are sufficient because they are the opposite of the above
	// and are always the exact inverse
	public void test_noMatchesFor_shouldReturnFalse_whenUnknownFamilyNameSet_andFamilyNameIsNull() {
		String familyName = null;
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname?,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("No matches should be false because a familyname is not required", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	// this, along with the next test are sufficient because they are the opposite of the above
	// and are always the exact inverse
	public void test_noMatchesFor_shouldReturnFalse_whenUnknownPIDNotSet_andPIDNotNull() {
		String pid = "PID";
		String issuer = "ISSUER";
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("No matches should be false because a PID is required, and is present", 
				pm.noMatchesFor(pid, issuer, familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	// this, along with the previous test are sufficient because they are the opposite of the above
	// and are always the exact inverse
	public void test_noMatchesFor_shouldReturnFalse_whenUnknownFamilyNameNotSet_andFamilyNameNotNull() {
		String familyName = "Smith";
		String givenName = "John";
		String middleName = "F";
		String prefix = "DR.";
		String suffix = "PH.D.";
		String birthDate = "19730920";
		String sex = "M";
		
		String s = "pid,issuer,[familyname,givenname,middlename,nameprefix,namesuffix,birthdate,sex]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("No matches should be false because a familyname is required, and is present", 
				pm.noMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
		
//	public void test_allMatchesFor_shouldReturnTrue_whenAllUnknownsAreSet_andAllValuesAreNull() {
//		String familyName = null;
//		String givenName = null;
//		String middleName = null;
//		String prefix = null;
//		String suffix = null;
//		
//		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
//		PatientMatching pm = new PatientMatching(s);
//		assertTrue("All matches should be true because all unknowns should match", 
//				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix));
//	}
//	
//	public void test_allMatchesFor_shouldReturnTrue_whenDemographicsNotSpecified_andAllValuesAreNull() {
//		String familyName = null;
//		String givenName = null;
//		String middleName = null;
//		String prefix = null;
//		String suffix = null;
//		
//		String s = "pid,issuer";
//		PatientMatching pm = new PatientMatching(s);
//		assertTrue("All matches should be true because no demographics are required", 
//				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix));
//	}
	
	public void test_allMatchesFor_shouldReturnTrue_whenAllUnknownsAreSet_andAllValuesAreNull_includesBirthDateAndSex() {
		String familyName = null;
		String givenName = null;
		String middleName = null;
		String prefix = null;
		String suffix = null;
		String birthDate = null;
		String sex = null;
		
		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("All matches should be true because all unknowns should match", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_allMatchesFor_shouldReturnTrue_whenDemographicsNotSpecified_andAllValuesAreNull_includesBirthDateAndSex() {
		String familyName = null;
		String givenName = null;
		String middleName = null;
		String prefix = null;
		String suffix = null;
		String birthDate = null;
		String sex = null;
		
		String s = "pid,issuer";
		PatientMatching pm = new PatientMatching(s);
		assertTrue("All matches should be true because no demographics are required", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_allMatchesFor_shouldReturnFalse_whenAllUnknownsAreSet_andFamilyNameIsNotNull() {
		String familyName = "Smith";
		String givenName = null;
		String middleName = null;
		String prefix = null;
		String suffix = null;
		String birthdate = null;
		String sex = null;
		
		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("All matches should be false because familyname is not an unknown value", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthdate, sex));
	}
	
	public void test_allMatchesFor_shouldReturnFalse_whenAllUnknownsAreSet_andGivenNameIsNotNull() {
		String familyName = null;
		String givenName = "John";
		String middleName = null;
		String prefix = null;
		String suffix = null;
		String birthdate = null;
		String sex = null;
		
		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("All matches should be false because givenname is not an unknown value", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthdate, sex));
	}
	
	public void test_allMatchesFor_shouldReturnFalse_whenAllUnknownsAreSet_andMiddleNameIsNotNull() {
		String familyName = null;
		String givenName = null;
		String middleName = "F";
		String prefix = null;
		String suffix = null;
		String birthdate = null;
		String sex = null;
		
		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("All matches should be false because middlename is not an unknown value", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthdate, sex));
	}
	
	public void test_allMatchesFor_shouldReturnFalse_whenAllUnknownsAreSet_andPrefixIsNotNull() {
		String familyName = null;
		String givenName = null;
		String middleName = null;
		String prefix = "DR.";
		String suffix = null;
		String birthdate = null;
		String sex = null;
		
		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("All matches should be false because prefix is not an unknown value", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthdate, sex));
	}
	
	public void test_allMatchesFor_shouldReturnFalse_whenAllUnknownsAreSet_andSuffixIsNotNull() {
		String familyName = null;
		String givenName = null;
		String middleName = null;
		String prefix = null;
		String suffix = "PH.D.";
		String birthdate = null;
		String sex = null;
		
		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("All matches should be false because suffix is not an unknown value", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthdate, sex));
	}
	
	public void test_allMatchesFor_shouldReturnFalse_whenAllUnknownsAreSet_andBirthDateIsNotNull() {
		String familyName = null;
		String givenName = null;
		String middleName = null;
		String prefix = null;
		String suffix = null;
		String birthDate = "19730920";
		String sex = null;
		
		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("All matches should be false because birthdate is not an unknown value", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	public void test_allMatchesFor_shouldReturnFalse_whenAllUnknownsAreSet_andSexIsNotNull() {
		String familyName = null;
		String givenName = null;
		String middleName = null;
		String prefix = null;
		String suffix = null;
		String birthDate = null;
		String sex = "M";
		
		String s = "pid,issuer,[familyname?,givenname?,middlename?,nameprefix?,namesuffix?,birthdate?,sex?]";
		PatientMatching pm = new PatientMatching(s);
		assertFalse("All matches should be false because sex is not an unknown value", 
				pm.allMatchesFor(familyName, givenName, middleName, prefix, suffix, birthDate, sex));
	}
	
	//////////////////////////////////////////////////////////////////////
	// Helper classes and methods
	//////////////////////////////////////////////////////////////////////
	private String makePersonName(String familyName, String givenName, String middleName, String prefix, String suffix) {
		return familyName + "^" + givenName + "^" + middleName + "^" + 
			prefix + "^" + suffix;
	}
	
	private static class ExceptionTrap {
		public static Exception capture(ExceptionTrigger trigger) {
			try {
				trigger.trip();
			}
			catch ( Exception e ) {
				return e;
			}
			return new NotAnExceptionException();
		}
	}
	
	private static interface ExceptionTrigger {
		public void trip();
	}
	
	private static class NotAnExceptionException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
