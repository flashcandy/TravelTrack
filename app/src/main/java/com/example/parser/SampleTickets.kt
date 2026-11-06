package com.example.parser

object SampleTickets {
    const val SAMPLE_IRCTC_ERS_TEXT = """
IRCTC Electronic Reservation Slip (ERS)
Booked from: KHAMMAM (KMT)   To: SECUNDERABAD JN. (SC)
Departure* 06:35 19-Nov-2026   Arrival* 10:50 19-Nov-2026
PNR: 6952208777   Train No./Name: 11020/KONARK EXPRESS   Class: SLEEPER CLASS (SL)
Quota: GENERAL (GN)   Booking Date: 10-Nov-2026 14:22:10 HRS

Passenger Details:
# Name Age Gender Booking Status Current Status
1. NOMULA SAI CHAND 30 M RLWL/29 RLWL/28

Total Fare (all inclusive): Rs. 201.80
Transaction ID: 100004128912345

Important Instructions:
1. One of the passengers must carry original identity card as printed on the ticket.
2. The authority to travel of passengers is subject to rules printed on the ticket.
Acronyms: RLWL: Remote Location Waitlist, PQWL: Pooled Quota Waitlist
"""

    const val SAMPLE_REDBUS_TSRTC_TEXT = """
From City KHAMMAM BS   To City KPHB   Date of Journey 15-Jan-2026
Depart On 11:55 PM   Start Time at Origin 15/01/2026 22:40 Hrs
Ticket No TS260115130655852823RERD#TG26011518365612WD2H##   Status Confirmed
Total Price 1250   Seats 15, 16   Passengers: VIJAYA NOMULA (45, FEMALE), SAI CHAND NOMULA (28, MALE)
"""

    const val SAMPLE_UNKNOWN_TICKET_TEXT = """
METRO EXPRESS BOARDING PASS
Pass Ref: MTX-994120
Route: HYDERABAD CENTRAL -> HI-TECH CITY
Date: 08-Nov-2026  Time: 09:15 AM
Amount Paid: Rs. 60.00
"""
}
