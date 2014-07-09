/*
 * Copyright 2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dcm4chex.archive.util;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Franz Willer
 * @version $Id: $
 * @since 12.06.2013
 */
public class DateTimeFormat extends SimpleDateFormat {
    private static final long serialVersionUID = 1L;
    private boolean rightMargin;
    
    public DateTimeFormat() {
        super("yyyy-MM-dd HH:mm:ss");
    }
    public DateTimeFormat(boolean rightMargin) {
        super("yyyy-MM-dd HH:mm:ss");
        this.rightMargin = rightMargin;
    }
    
    public Date parse(String source, ParsePosition pos) {
        calendar.clear();
        int p = 0;
        try {
            String s = parseTZ(source);
            int l = s.length();
            calendar.set(Calendar.YEAR,
                Integer.parseInt(s.substring(p,p+4)));
            p += 4;
            if (l > p) {
                if (!Character.isDigit(s.charAt(p))) {
                    ++p;
                }
                calendar.set(Calendar.MONTH,
                    Integer.parseInt(s.substring(p,p+2)) - 1);
                p += 2;
                if (l > p) {
                    if (!Character.isDigit(s.charAt(p))) {
                        ++p;
                    }
                    calendar.set(Calendar.DAY_OF_MONTH,
                        Integer.parseInt(s.substring(p,p+2)));
                    p += 2;
                    if (l > p) {
                        if (!Character.isDigit(s.charAt(p))) {
                            ++p;
                        }
                        calendar.set(Calendar.HOUR_OF_DAY,
                            Integer.parseInt(s.substring(p,p+2)));
                        p += 2;
                        if (l > p) {
                            if (s.charAt(p) == ':') {
                                ++p;
                            }
                            calendar.set(Calendar.MINUTE,
                                Integer.parseInt(s.substring(p,p+2)));
                            p+=2;
                            if (l > p) {
                                if (s.charAt(p) == ':') {
                                    ++p;
                                }
                                calendar.set(Calendar.SECOND, Integer.parseInt(s.substring(p)));
                            } else {
                                clearCalendarFields(Calendar.SECOND);
                            }
                        } else {
                            clearCalendarFields(Calendar.MINUTE);
                        }
                    } else {
                        clearCalendarFields(Calendar.HOUR_OF_DAY);
                    }
                } else {
                    clearCalendarFields(Calendar.DAY_OF_MONTH);
                }
            } else {
                clearCalendarFields(Calendar.MONTH);
            }
            pos.setIndex(source.length());
            return calendar.getTime();
        } catch (Exception e) {
            pos.setErrorIndex(p);
            return null;
        }
    }
    
    private String parseTZ(String source) {
        int zpos = source.length() - 5;
        if (zpos >= 0) {
            char ch = source.charAt(zpos);
            if (ch == '+' || ch == '-') {
                int off = Integer.parseInt(source.substring(zpos+1));                
                calendar.set(Calendar.ZONE_OFFSET, ch == '-' ? -off : off);
                calendar.set(Calendar.DST_OFFSET, 0);
                return source.substring(0, zpos);
            }
        }
        return source;
    }    

    private void clearCalendarFields(int field) {
        if (rightMargin) {
            switch (field) {
                case Calendar.MONTH:
                    calendar.set(Calendar.MONTH, 11);
                case Calendar.DAY_OF_MONTH:
                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                case Calendar.HOUR_OF_DAY:
                    calendar.set(Calendar.HOUR_OF_DAY, 23);
                case Calendar.MINUTE:
                    calendar.set(Calendar.MINUTE, 59);
                case Calendar.SECOND:
                    calendar.set(Calendar.SECOND, 59);
                default:
                    calendar.set(Calendar.MILLISECOND, 999);
            }
        }
    }
}