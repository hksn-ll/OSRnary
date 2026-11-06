import os
import textwrap
from PIL import Image, ImageDraw, ImageFont
import docx
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import qn, nsdecls

# ==============================================================================
# 1. XML & STYLING HELPERS (MEDSYNC BENCHMARK SPECIFICATION)
# ==============================================================================

def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=110, bottom=110, left=150, right=150):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = OxmlElement('w:tcMar')
    for m, val in [('top', top), ('bottom', bottom), ('left', left), ('right', right)]:
        node = OxmlElement(f'w:{m}')
        node.set(qn('w:w'), str(val))
        node.set(qn('w:type'), 'dxa')
        tcMar.append(node)
    tcPr.append(tcMar)

def set_table_borders(table, color="CBD5E1", sz="4", val="single"):
    tblPr = table._tbl.tblPr
    borders = parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        f'<w:top w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:bottom w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:insideH w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:left w:val="none"/>'
        f'<w:right w:val="none"/>'
        f'<w:insideV w:val="none"/>'
        f'</w:tblBorders>'
    )
    tblPr.append(borders)

def add_horizontal_rule(doc, color="5341CD", sz="12"):
    p = doc.add_paragraph()
    pPr = p._p.get_or_add_pPr()
    pBdr = parse_xml(
        f'<w:pBdr {nsdecls("w")}>'
        f'<w:bottom w:val="single" w:sz="{sz}" w:space="1" w:color="{color}"/>'
        f'</w:pBdr>'
    )
    pPr.append(pBdr)
    return p

def add_page_number_footer(section):
    footer = section.footer
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run()
    run.font.name = 'Times New Roman'
    run.font.size = Pt(10)
    run.font.color.rgb = RGBColor(0x55, 0x55, 0x55)

    fldChar1 = parse_xml(r'<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w'))
    instrText = parse_xml(r'<w:instrText %s xml:space="preserve"> PAGE </w:instrText>' % nsdecls('w'))
    fldChar2 = parse_xml(r'<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w'))
    fldChar3 = parse_xml(r'<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w'))
    run._r.append(fldChar1)
    run._r.append(instrText)
    run._r.append(fldChar2)
    run._r.append(fldChar3)

def add_toc_field(doc):
    p = doc.add_paragraph()
    run = p.add_run()
    fldChar1 = parse_xml(r'<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w'))
    instrText = parse_xml(r'<w:instrText %s xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText>' % nsdecls('w'))
    fldChar2 = parse_xml(r'<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w'))
    fldChar3 = parse_xml(r'<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w'))
    run._r.append(fldChar1)
    run._r.append(instrText)
    run._r.append(fldChar2)
    run._r.append(fldChar3)

def add_styled_table(doc, headers, rows):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = True
    set_table_borders(table)

    hdr_cells = table.rows[0].cells
    for i, header_text in enumerate(headers):
        hdr_cells[i].text = ""
        set_cell_background(hdr_cells[i], "5341CD")
        set_cell_margins(hdr_cells[i], top=130, bottom=130, left=150, right=150)
        p = hdr_cells[i].paragraphs[0]
        r = p.add_run(header_text)
        r.bold = True
        r.font.name = 'Times New Roman'
        r.font.size = Pt(11)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)

    for r_idx, row_data in enumerate(rows):
        row_cells = table.add_row().cells
        bg = "F8FAFC" if r_idx % 2 == 1 else "FFFFFF"
        for c_idx, val in enumerate(row_data):
            row_cells[c_idx].text = ""
            set_cell_background(row_cells[c_idx], bg)
            set_cell_margins(row_cells[c_idx], top=100, bottom=100, left=150, right=150)
            p = row_cells[c_idx].paragraphs[0]
            r = p.add_run(val)
            r.font.name = 'Times New Roman'
            r.font.size = Pt(10.5)
            r.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
            if c_idx == 0:
                r.bold = True

    doc.add_paragraph()

def add_heading_1(doc, text):
    p = doc.add_heading(level=1)
    p.paragraph_format.space_before = Pt(14)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    r.font.size = Pt(18)
    r.bold = True
    r.font.color.rgb = RGBColor(0x11, 0x11, 0x11)
    return p

def add_heading_2(doc, text):
    p = doc.add_heading(level=2)
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    r.font.size = Pt(14)
    r.bold = True
    r.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
    return p

def add_heading_3(doc, text):
    p = doc.add_heading(level=3)
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    r.font.size = Pt(12)
    r.bold = True
    r.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    return p

def add_body_p(doc, text, bold_prefix=None):
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing = 1.15
    p.paragraph_format.space_after = Pt(6)
    if bold_prefix:
        rb = p.add_run(bold_prefix)
        rb.bold = True
        rb.font.name = 'Times New Roman'
        rb.font.size = Pt(12)
        rb.font.color.rgb = RGBColor(0x11, 0x11, 0x11)
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    r.font.size = Pt(12)
    r.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
    return p

def add_bullet_p(doc, bold_prefix, text):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.line_spacing = 1.15
    p.paragraph_format.space_after = Pt(4)
    rb = p.add_run(bold_prefix)
    rb.bold = True
    rb.font.name = 'Times New Roman'
    rb.font.size = Pt(12)
    rb.font.color.rgb = RGBColor(0x11, 0x11, 0x11)
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    r.font.size = Pt(12)
    r.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
    return p

# ==============================================================================
# 2. NON-TECHNICAL PLACEHOLDER & SCREENSHOT EMBEDDING ENGINE
# ==============================================================================

PLACEHOLDER_DIR = r"manual_assets\screenshots\placeholders"
os.makedirs(PLACEHOLDER_DIR, exist_ok=True)

def create_placeholder_image(filename, fig_label, title, capture_hint, is_mobile=False):
    """Generates a clean, proportioned card image in plain user language so the user
    can right-click -> 'Change Picture' in Word while keeping layout dimensions intact."""
    path = os.path.join(PLACEHOLDER_DIR, filename)
    w, h = (720, 1280) if is_mobile else (1280, 720)
    img = Image.new("RGB", (w, h), color=(248, 250, 252))
    draw = ImageDraw.Draw(img)

    # Outer border & inner frame
    border_color = (83, 65, 205)
    draw.rectangle([8, 8, w - 9, h - 9], outline=border_color, width=6)
    draw.rectangle([24, 24, w - 25, h - 25], outline=(203, 213, 225), width=2)

    # Header banner
    draw.rectangle([24, 24, w - 25, 110], fill=(83, 65, 205))

    try:
        font_lg = ImageFont.truetype("arialbd.ttf", 28)
        font_md = ImageFont.truetype("arialbd.ttf", 24)
        font_sm = ImageFont.truetype("arial.ttf", 20)
    except Exception:
        font_lg = ImageFont.load_default()
        font_md = ImageFont.load_default()
        font_sm = ImageFont.load_default()

    draw.text((48, 52), f"PHOTO PLACEHOLDER — {fig_label.upper()}", fill=(255, 255, 255), font=font_lg)

    # Title & Capture instructions in plain English
    y_cursor = 170
    wrap_width = 38 if is_mobile else 70
    for line in textwrap.wrap(title, width=wrap_width):
        draw.text((48, y_cursor), line, fill=(15, 23, 42), font=font_md)
        y_cursor += 36

    y_cursor += 25
    draw.text((48, y_cursor), "Screen to Show:", fill=(83, 65, 205), font=font_md)
    y_cursor += 36
    for line in textwrap.wrap(capture_hint, width=wrap_width + 6):
        draw.text((48, y_cursor), line, fill=(71, 85, 105), font=font_sm)
        y_cursor += 30

    y_cursor += 40
    tip = "How to replace: Right-click this box in Word -> 'Change Picture' -> 'From a File' or paste your screenshot here."
    for line in textwrap.wrap(tip, width=wrap_width + 6):
        draw.text((48, y_cursor), line, fill=(100, 116, 139), font=font_sm)
        y_cursor += 28

    img.save(path, "PNG")
    return path

def add_figure_block(doc, fig_num, title, overview_text, steps, existing_img_path=None, is_mobile=False, capture_hint=""):
    """Writes a clean, accessible procedural section:
    - Heading 3
    - Plain-language overview paragraph
    - Easy-to-follow numbered steps
    - Centered screenshot or clean placeholder card
    - Centered italic Figure caption
    """
    add_heading_3(doc, f"Figure {fig_num}: {title}")
    add_body_p(doc, overview_text)

    for idx, step_text in enumerate(steps, 1):
        p = doc.add_paragraph()
        p.paragraph_format.left_indent = Inches(0.25)
        p.paragraph_format.line_spacing = 1.15
        p.paragraph_format.space_after = Pt(4)
        r_num = p.add_run(f"{idx}. ")
        r_num.bold = True
        r_num.font.name = 'Times New Roman'
        r_num.font.size = Pt(11.5)
        r_num.font.color.rgb = RGBColor(0x53, 0x41, 0xCD)

        r_txt = p.add_run(step_text)
        r_txt.font.name = 'Times New Roman'
        r_txt.font.size = Pt(11.5)
        r_txt.font.color.rgb = RGBColor(0x22, 0x22, 0x22)

    width_inches = 2.35 if is_mobile else 5.5
    if existing_img_path and os.path.exists(existing_img_path) and os.path.getsize(existing_img_path) > 5000:
        img_to_use = existing_img_path
    else:
        ph_file = f"placeholder_fig_{fig_num:02d}.png"
        hint = capture_hint or f"Show the {title} screen ({'Mobile Phone Screen' if is_mobile else 'Computer Web Browser Screen'})."
        img_to_use = create_placeholder_image(ph_file, f"Figure {fig_num}", title, hint, is_mobile=is_mobile)

    p_img = doc.add_paragraph()
    p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_img.paragraph_format.space_before = Pt(6)
    p_img.paragraph_format.space_after = Pt(3)
    p_img.paragraph_format.keep_with_next = True
    run_img = p_img.add_run()
    run_img.add_picture(img_to_use, width=Inches(width_inches))

    p_cap = doc.add_paragraph()
    p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_cap.paragraph_format.space_after = Pt(14)
    r_cap = p_cap.add_run(f"Figure {fig_num}: {title}")
    r_cap.italic = True
    r_cap.font.name = 'Times New Roman'
    r_cap.font.size = Pt(10)
    r_cap.font.color.rgb = RGBColor(0x55, 0x55, 0x55)


# ==============================================================================
# 3. NON-TECHNICAL 78-FIGURE SPECIFICATION DATA (PLAIN LANGUAGE)
# ==============================================================================

SUPER_ADMIN_FIGURES = [
    (
        1,
        "Super Admin Login Screen",
        "The Web Administrative Portal login screen allows authorized division leaders and school administrators to sign in from any office computer or laptop using their official email address and password.",
        [
            "Open your web browser and go to the GabAI Administrative Portal website.",
            "Type your official division administrator email address in the 'Administrative Email' box.",
            "Type your password in the 'Secure Password' box, and optionally check 'Keep session active' if you are using your personal office computer.",
            "Click the blue 'Sign In to Management Portal' button to open the Division Overview dashboard."
        ],
        r"manual_assets\screenshots\web\fig_01_super_admin_login.png",
        False,
        "Show the Web Admin Portal login page with the Administrative Email and Secure Password boxes."
    ),
    (
        2,
        "Division Administrator First-Time Setup Window",
        "During initial setup at the Division Office, authorized officers can create the primary Division Administrator account using the official division setup key issued by the Division Office.",
        [
            "On the login page, click the 'Provision Admin Key' link under 'First-Time Setup?'.",
            "Enter the administrator's First Name, Last Name, Official DepEd Email Address, and choose a secure password.",
            "Enter the official Division Setup Key provided by the Division Office.",
            "Click 'Create & Authorize Admin' to finish creating your administrator account and sign in immediately."
        ],
        None,
        False,
        "Click 'Provision Admin Key' on the login page to show the Division Setup popup window."
    ),
    (
        3,
        "Welcome Guide & Interactive Tour",
        "The online portal includes a friendly Help Guide and interactive walkthrough that highlights navigation menus, school filters, reading charts, and assessment tools for new administrators.",
        [
            "Click the 'Help & Tour' button located at the top-right corner of the screen.",
            "Read the quick role guide or click 'Start Interactive Spotlight Tour'.",
            "Click 'Next' and 'Back' to step through each section of the portal, or click 'End Tour' to return to your work."
        ],
        None,
        False,
        "Click 'Help & Tour' in the top right header to show the welcome guide and tour window."
    ),
    (
        4,
        "Division Reading & Literacy Overview Dashboard",
        "The Division Executive Overview is the main dashboard for division supervisors, displaying summary counts for participating junior high schools, total enrolled learners, active reading assessments, and division-wide reading scores.",
        [
            "Click 'Executive Overview' on the left navigation menu.",
            "Use the 'Jurisdiction' dropdown at the top to view all schools together or select a specific school to inspect.",
            "Switch between 'All Overview', 'Visual Charts', and 'Data Tables' to view the information in your preferred format.",
            "Click the 'Print Executive Briefing' button to generate a clean, one-click PDF summary report for meetings."
        ],
        r"manual_assets\screenshots\web\fig_02_super_admin_dashboard.png",
        False,
        "Show the Division Executive Overview dashboard with summary cards and the executive briefing banner."
    ),
    (
        5,
        "Reading Performance Charts & Score Guide",
        "The Visual Reading Analytics section displays easy-to-read charts showing student reading mastery levels, comparison between male and female learners, grade-level benchmarks, and student assessment progress.",
        [
            "Scroll down on the Executive Overview page to the 'Visual Reading Analytics Hub'.",
            "View the charts showing reading levels (Independent, Instructional, and Support) and grade-level progress.",
            "Click the 'How This Works' info button on any chart card to view a clear explanation of how the scores are calculated and what they mean for reading intervention.",
            "Click 'Close' after reviewing the explanation."
        ],
        None,
        False,
        "Scroll down to the charts on the Executive Overview and click 'How This Works' on a chart to show the explanation window."
    ),
    (
        6,
        "School Directory & Add New School Window",
        "The School Directory lists all public Junior High Schools in Valenzuela City with their official DepEd School ID, congressional district, assigned school principal, and student counts. Division administrators can easily add new schools.",
        [
            "Click 'School Directory' on the left menu.",
            "Click the '+ Add School' button at the top right of the table.",
            "Enter the official 6-digit DepEd School ID, the full School Name, and choose the Congressional District (District 1 or District 2).",
            "Click 'Save School' to add the school to the system so teachers and students can select it immediately."
        ],
        r"manual_assets\screenshots\web\fig_03_campus_management.png",
        False,
        "On the School Directory page, click '+ Add School' and show the Add Junior High School popup window."
    ),
    (
        7,
        "Assign School Principal / Administrator Window",
        "Division administrators can directly assign a School Principal, Department Head, or School Coordinator to manage a specific school campus.",
        [
            "In the School Directory table, find the school and click the 'Provision Admin' button on that row.",
            "Check that the correct school name and ID appear at the top of the window.",
            "Enter the administrator's First Name, Last Name, Position (e.g., School Principal), Official DepEd Email, and an initial password.",
            "Click 'Create & Authorize School Admin' to create their login account."
        ],
        r"manual_assets\screenshots\web\fig_04_admin_provisioning.png",
        False,
        "In the School Directory, click 'Provision Admin' on a school row to show the Assign School Administrator popup window."
    ),
    (
        8,
        "Official School Invite Code & Archive Protection",
        "Division administrators can also create an official School Invite Code for a principal to claim their school, or safely remove an inactive school with automatic 30-day backup protection.",
        [
            "In the School Directory, click 'Issue Invite' next to any school to generate a one-time school invite code.",
            "Copy the code shown on the screen and share it with the designated School Principal.",
            "To remove an inactive school, click 'Remove', review the confirmation message showing current teachers and students, and confirm to move it safely to the 30-day Recycle Bin."
        ],
        None,
        False,
        "In the School Directory, click 'Issue Invite' on any school row to show the School Invite Code display window."
    ),
    (
        9,
        "Division Announcements & Directives Broadcast",
        "Division leaders can broadcast official memorandums, reading campaign advisories, and school notices to all campuses, teachers, or students.",
        [
            "Click 'Announcements' on the left menu.",
            "Click the '+ New Announcement' button.",
            "Enter the Announcement Title, select the Category (e.g., Official Memo or Urgent Advisory), and choose who should receive it.",
            "Type your announcement message and click 'Publish Broadcast' to post it immediately to web and mobile screens."
        ],
        None,
        False,
        "On the Announcements page, click '+ New Announcement' to show the announcement creation window."
    ),
    (
        10,
        "Announcement Read Receipts & Replies Window",
        "Division administrators can see exactly who has opened and read each announcement, and read or answer questions sent by school principals and teachers.",
        [
            "Find an announcement card on the Announcements page and click the 'Viewers' button.",
            "Filter the list by All Viewers, Teachers, or Students to see who has read the bulletin and when.",
            "Click the 'Replies' button on the announcement card to view feedback or questions from teachers and send an official response."
        ],
        None,
        False,
        "On an announcement card, click 'Read Receipts' or 'Replies' to show the viewer list window."
    ),
    (
        11,
        "Deleted Items Recycle Bin (30-Day Recovery)",
        "The Recycle Bin protects all deleted schools, teachers, classes, student accounts, reading files, and quizzes. Any item deleted by mistake is safely kept for 30 days and can be restored with a single click.",
        [
            "Click 'Recycle Bin' on the left navigation menu.",
            "Use the category buttons (Students, Materials, Subjects, Schools, Teachers, Classes, Assessments) to find what you are looking for.",
            "Click the eye icon on any row to preview the details of the deleted item.",
            "Click 'Restore' to return the item back to active use immediately, or click the trash can icon if you wish to permanently remove it."
        ],
        r"manual_assets\screenshots\web\fig_05_system_logs.png",
        False,
        "Show the Recycle Bin page with the category filter buttons and the Restore action buttons."
    ),
    (
        12,
        "Division Faculty Directory & Teacher Status",
        "The Faculty & Approvals section gives division leaders a complete list of all registered teachers across the division, showing their school, approval status, and active classes.",
        [
            "Click 'Faculty & Approvals' on the left menu.",
            "Use the search box or school filter to find a teacher by name or school.",
            "Check whether each teacher is approved or awaiting school verification, and review their teaching assignments."
        ],
        None,
        False,
        "Click 'Faculty & Approvals' on the left menu to show the Division Faculty Directory table."
    ),
    (
        13,
        "Division Classroom Sections & Join Codes",
        "Division administrators can view all Grade 7 to Grade 10 class sections across schools, including the class adviser, 6-character class code, and number of enrolled learners.",
        [
            "Click 'Classes & Section Codes' on the left menu.",
            "Filter sections by School or Grade Level, or type a section name in the search box.",
            "Click on any class row to see the student list and check if the class has set up its introductory reading assessment."
        ],
        None,
        False,
        "Click 'Classes & Section Codes' on the left menu to show the classroom sections list."
    ),
    (
        14,
        "Student Reading Performance & Gender Reports",
        "The Learner Performance section displays student reading progress across all schools, showing results separated by male and female learners (DepEd SF1 format) and identifying students who need extra reading support.",
        [
            "Click 'Learner Performance' on the left menu.",
            "Filter by School, Grade Level, Section, Gender (Male or Female), or Reading Level (Independent, Instructional, or Support).",
            "Click on any student to review their complete quiz history, or click 'Export CSV / PDF' to download official reading reports for school records."
        ],
        None,
        False,
        "Click 'Learner Performance' on the left menu to show the student reading performance table and filters."
    ),
    (
        15,
        "Student Accounts & Password Reset Vault",
        "The Student Accounts & Passwords section allows administrators to view all generated student usernames, check which students have logged in, and help reset forgotten passwords.",
        [
            "Click 'Student Accounts & Passwords' on the left menu.",
            "Filter by class section or search for a student by their full name or username.",
            "View temporary passwords for newly added learners, or click 'Reset Password' to give a student a new temporary password so they can log back in."
        ],
        None,
        False,
        "Click 'Student Accounts & Passwords' on the left menu to show the student accounts list."
    ),
    (
        16,
        "Create & Deploy Division Reading Assessments (Phil-IRI Pre-Test & Post-Test)",
        "Division supervisors can schedule uniform, timed reading assessments for all junior high schools, including official Phil-IRI Pre-Tests (beginning of year) and Post-Tests (end of year) with reading passages and comprehension questions.",
        [
            "Click 'Standardized Reading Tests' on the left menu.",
            "Click the '+ Create Timed Assessment' button.",
            "Enter the Test Title, select the Test Type (Phil-IRI Pre-Test, Post-Test, or Uniform Reading Check), choose the Target Grade Level, and set the time limit in minutes.",
            "Add the reading passage, enter the multiple-choice questions tagged with reading skills (Literal, Inferential, Critical, Vocabulary), and click 'Deploy Assessment' to send it to all student apps."
        ],
        None,
        False,
        "Click 'Standardized Reading Tests' on the left menu and open the '+ Create Timed Assessment' window."
    ),
    (
        17,
        "Reading Skills Mastery & Pre/Post-Test Growth Matrix",
        "Within the Standardized Reading Tests section, administrators can view summary tables showing how students performed across different reading skills and compare Pre-Test and Post-Test scores to measure reading improvement.",
        [
            "On the 'Standardized Reading Tests' page, scroll down to the 'Reading Competency Mastery Matrix' and 'Pre-Test vs. Post-Test Growth Matrix' tables.",
            "Compare average scores across Literal, Inferential, Critical, and Vocabulary questions by school and grade level.",
            "Look at the Growth column to see how much reading ability has improved and identify schools or sections that need additional reading materials."
        ],
        None,
        False,
        "Scroll down on the Standardized Reading Tests page to show the Competency Mastery and Pre/Post Growth tables."
    ),
    (
        18,
        "Curriculum Review — Reading Modules, Weekly Quizzes & Quiz Records",
        "Division administrators can browse all teacher-uploaded PDF reading materials, review weekly classroom quizzes created by teachers, and view individual student quiz attempts.",
        [
            "Click 'Reading Materials & Modules' to browse subject folders, preview reading lesson PDFs, and inspect question pools.",
            "Click 'Weekly Classroom Quizzes' to monitor ongoing quizzes created by classroom teachers and check student submission rates.",
            "Click 'Quiz Attempt Logs' to review individual quiz scores and answer records across all assessment activities."
        ],
        None,
        False,
        "Click 'Reading Materials & Modules', 'Weekly Classroom Quizzes', or 'Quiz Attempt Logs' on the left menu to show the review table."
    ),
    (
        19,
        "System Settings & AI Assistant Setup (Division Admin Only)",
        "The System Settings page is accessible only to Division Super Administrators to manage platform security, configure the AI reading assistant, and update the division setup key when needed.",
        [
            "Click 'System Settings' at the bottom of the left menu.",
            "Review or update the smart reading assistant settings used for word explanations and quiz question generation.",
            "Optionally update the division setup key or system maintenance settings, then click 'Save System Configuration'."
        ],
        None,
        False,
        "Click 'System Settings' at the bottom of the left menu as a Division Admin and show the System Settings page."
    ),
]

SCHOOL_ADMIN_FIGURES = [
    (
        20,
        "School Principal Account Claim Window",
        "When a School Principal or School Coordinator receives an official School Invite Code from the Division Office, they can activate their school administration account directly from the login page.",
        [
            "On the portal login page, click the 'Claim School Account' link inside the 'School Principal / IT?' card.",
            "Enter the official School Invite Code (e.g., SDO-305445-ABCD) issued by the Division Office.",
            "Enter your First Name, Last Name, Official DepEd Email, and choose your password.",
            "Click 'Claim & Activate School Portal' to sign in and take charge of your school's dashboard."
        ],
        None,
        False,
        "On the login page, click 'Claim School Account' to show the Claim School Portal popup window."
    ),
    (
        21,
        "School Reading Overview Dashboard",
        "When signed in as a School Principal or Administrator, the dashboard focuses exclusively on your school campus, showing active teachers, pending teacher approvals, classroom sections, enrolled students, and school reading scores.",
        [
            "Sign in with your School Administrator account to open your School Overview dashboard.",
            "Check the top banner for any active memorandums or directives sent by the Division Office.",
            "Look at the 'Pending Approvals' card to see if any newly registered teachers are waiting for your approval.",
            "Review your school's overall reading score, student numbers, and male/female reading progress, or click 'Print Executive Briefing' to print a school report."
        ],
        r"manual_assets\screenshots\web\fig_06_school_admin_dashboard.png",
        False,
        "Show the School Administrator Overview dashboard with the School Authority badge and Pending Approvals card."
    ),
    (
        22,
        "School Announcements & Bulletins",
        "School Administrators can post school-wide notices or section-specific reminders for teachers and students, and verify who has read them.",
        [
            "Click 'Announcements' on the left menu.",
            "Click '+ New Announcement', type the title and message, and choose whether to send it to the entire school, teachers only, or specific student grade levels.",
            "Publish the notice and click the 'Read Receipts' button on the announcement card to verify which teachers and students have opened it."
        ],
        None,
        False,
        "Click 'Announcements' on the left menu as a School Admin to show the school announcement feed."
    ),
    (
        23,
        "Teacher Verification & Approval Queue",
        "To protect student safety and ensure only legitimate faculty members access the system, all teachers who register on the mobile app appear in the approval queue until approved by their school principal.",
        [
            "Click 'Faculty & Approvals' on the left menu.",
            "Locate newly registered teachers marked with the orange 'Pending Verification' badge.",
            "Verify the teacher's name and email address, then click the green 'Approve' button to give them immediate access to their mobile teaching tools.",
            "Use the 'Revoke' button if a teacher leaves or transfers to another school."
        ],
        None,
        False,
        "Click 'Faculty & Approvals' as a School Admin showing a pending teacher row with the Approve button."
    ),
    (
        24,
        "School Class Sections & Section Codes",
        "School Administrators can review all Grade 7 to 10 class sections created by their teachers, check join codes, and see how many students are enrolled.",
        [
            "Click 'Classes & Section Codes' on the left menu.",
            "Browse through the sections or filter by grade level to locate a specific class.",
            "Review the section name, subject, homeroom adviser, 6-character class code, and enrolled student counts."
        ],
        None,
        False,
        "Click 'Classes & Section Codes' as a School Admin to show the school's class sections list."
    ),
    (
        25,
        "School Student Reading Performance & Remedial Support",
        "The School Learner Performance page helps principals and department heads monitor reading mastery across all students in the school, separated by male and female learners, and easily spot students who need reading intervention.",
        [
            "Click 'Learner Performance' on the left menu.",
            "Filter by Grade Level, Section, Gender (Male or Female), or Reading Level (Support / Remedial).",
            "Click on any student to review their reading test scores and quiz attempts, or export the school reading summary to Excel/PDF."
        ],
        None,
        False,
        "Click 'Learner Performance' as a School Admin to show the school student reading progress table."
    ),
    (
        26,
        "School Student Accounts & Password Assistance",
        "School Administrators can help teachers and students manage student usernames and issue temporary passwords whenever students forget their login details.",
        [
            "Click 'Student Accounts & Passwords' on the left menu.",
            "Filter by class section or search for a student by name or username.",
            "Click 'Reset Password' next to any student who forgot their password to create a new temporary password they can use to log in."
        ],
        None,
        False,
        "Click 'Student Accounts & Passwords' as a School Admin to show the school student credential list."
    ),
    (
        27,
        "School Timed Reading Tests Monitor",
        "School Administrators can track student participation and scores on division-wide timed reading tests (Phil-IRI Pre-Tests and Post-Tests) for their school.",
        [
            "Click 'Standardized Reading Tests' on the left menu.",
            "Check how many students in each section have completed the timed reading test.",
            "Review the school's reading skill breakdown and Pre-Test to Post-Test growth scores to evaluate reading progress."
        ],
        None,
        False,
        "Click 'Standardized Reading Tests' as a School Admin to show the school-level reading test progress."
    ),
    (
        28,
        "School Reading Materials & Principal Feedback Drawer",
        "Principals and department heads can review all lesson PDFs and reading modules uploaded by their teachers, and send helpful teaching feedback or suggestions directly to the teacher.",
        [
            "Click 'Reading Materials & Modules' on the left menu.",
            "Open any subject folder to see uploaded reading modules and preview the reading text or quiz questions.",
            "Click the 'Remarks / Guidance' button on any reading file to open the feedback drawer, type a helpful note or suggestion for the teacher, and click 'Send Remark'."
        ],
        None,
        False,
        "On the Reading Materials page, open the Principal Feedback Drawer on a reading file to show the note window."
    ),
    (
        29,
        "School Weekly Quizzes & Student Attempt Records",
        "School Administrators can monitor ongoing weekly reading quizzes created by classroom teachers and inspect detailed student quiz results.",
        [
            "Click 'Weekly Classroom Quizzes' to see all weekly quizzes assigned by teachers across sections.",
            "Click 'Quiz Attempt Logs' to see when students took quizzes and their scores.",
            "Click 'Inspect Attempt' on any quiz record to see the exact questions, the student's selected answers, and the correct answers."
        ],
        None,
        False,
        "Click 'Weekly Classroom Quizzes' or 'Quiz Attempt Logs' as a School Admin to show the quiz monitor."
    ),
    (
        30,
        "School Deleted Items Recovery (Recycle Bin)",
        "The School Recycle Bin displays only items deleted within your school (such as classes, reading files, or student accounts), allowing quick one-click recovery within 30 days.",
        [
            "Click 'Recycle Bin' on the left menu.",
            "Filter by category (Students, Materials, Subjects, Classes) to find the deleted item.",
            "Click 'Restore' to return the item back to active use immediately."
        ],
        None,
        False,
        "Click 'Recycle Bin' as a School Admin to show the school-level recovery page."
    ),
]

TEACHER_MOBILE_FIGURES = [
    (
        31,
        "Role Selection Screen — Educator Tab",
        "When opening the GabAI mobile app, the login screen provides two clear tabs at the top so teachers and students can easily open their respective login pages.",
        [
            "Open the GabAI app on your phone.",
            "Tap the '🍎 Educator' tab at the top of the card.",
            "Notice that the screen updates to 'Educator Portal 📖' where teachers can log in or create a new teacher account."
        ],
        r"manual_assets\screenshots\mobile\fig_07_role_selection.png",
        True,
        "On the mobile app login screen, tap the 'Educator' tab to show 'Educator Portal 📖'."
    ),
    (
        32,
        "Teacher Registration Form",
        "New teachers can create their account directly in the mobile app by entering their name, selecting their school from the list, and entering their official email address.",
        [
            "On the Educator login screen, tap 'New Educator? Create an Account' at the bottom.",
            "Enter your First Name and Last Name.",
            "Tap 'Select School' and choose your assigned Junior High School from the dropdown list.",
            "Enter your official Email Address, create a secure password, re-enter it to confirm, and tap 'Create Educator Account ➔'."
        ],
        None,
        True,
        "On the mobile app, tap 'New Educator? Create an Account', fill in name, school, email, and password, and show the form."
    ),
    (
        33,
        "Email Verification & Pending Principal Approval Notice",
        "After registering, the app sends an email confirmation link. If a teacher attempts to sign in before their school principal approves their account, a friendly notice explains that their account is awaiting verification.",
        [
            "Tap 'Create Educator Account ➔' to submit your registration and check your email for the confirmation link.",
            "If you sign in before approval, read the 'Account Pending Approval' notice reminding you that your School Principal will activate your account.",
            "Once your School Principal clicks 'Approve' on the school portal, sign in with your email and password to enter your Teacher Dashboard."
        ],
        None,
        True,
        "Try signing in with an unapproved teacher account on the mobile app to show the 'Account Pending Approval' notice."
    ),
    (
        34,
        "Teacher Home Dashboard",
        "The Teacher Home Dashboard displays your name, assigned school, active class count, total advisory students, quick-action cards, and school announcements.",
        [
            "Sign in with your approved Teacher account to open your Teacher Home Dashboard.",
            "Check your name, school name, and number of active classes at the top of the screen.",
            "Tap any of the quick-action cards ('Manage Classes', 'Teacher Library', 'Student Performance', or 'Announcements') to open that tool."
        ],
        None,
        True,
        "Sign in as an approved Teacher on the mobile app to show the Teacher Home Dashboard."
    ),
    (
        35,
        "Post Class Announcement, View Receipts & Student Replies",
        "Teachers can post announcements directly to their class sections, check which students have read each post, and read or reply to student comments.",
        [
            "On the Teacher Home Dashboard, tap '+ Post Announcement' in the Announcements section.",
            "Enter the announcement title, message, priority tag, choose your class section, and tap 'Publish'.",
            "Tap any posted announcement to see which students have read it (Read Receipts) or tap 'Replies' to answer student questions."
        ],
        None,
        True,
        "On the Teacher Home Dashboard, tap '+ Post Announcement' (or open Read Receipts / Replies) to show the announcement window."
    ),
    (
        36,
        "Class Management & Create New Class Screen",
        "The Manage Classes screen lists all your teaching sections with convenient search, student counts, and a simple button to create a new class.",
        [
            "From the Teacher Home Dashboard, tap the 'Manage Classes' card.",
            "Use the search bar at the top to quickly find a section by name or grade level.",
            "Tap '+ Create Class', enter the Section Name, Subject Name, Grade Level (Grade 7 to 10), and tap 'Create Class'."
        ],
        None,
        True,
        "Open 'Manage Classes' on the Teacher app to show your class cards or the 'Create Class' window."
    ),
    (
        37,
        "Class Details & Student Roster Screen",
        "Tapping any class card opens the Class Details screen, showing the student list organized by boys and girls, the 6-character Class Code, and a full-screen QR code for easy student enrollment.",
        [
            "Tap any class card on the Manage Classes screen to open its details.",
            "View the class header showing the Grade Level, Section Name, and 6-character Class Code.",
            "Tap the QR Code icon to display a large QR code on your phone screen so students can join your class instantly by pointing their phone camera at your screen.",
            "Scroll down to browse your student roster."
        ],
        None,
        True,
        "Open a class on the Teacher app to show the class header, Class Code, QR button, and student roster."
    ),
    (
        38,
        "Generate Student Accounts — Simple One-Box Paste",
        "Teachers can create student accounts in seconds by pasting their entire class roster into a single text box directly from an attendance sheet or text file.",
        [
            "Inside your class details screen, tap the '+ Generate Students' / 'Add Students' button.",
            "Paste or type student names into the single multiline text box, separated by new lines or commas (for example: 'Juan Dela Cruz, Maria Clara, Pedro Santos').",
            "Tap 'Generate Accounts'. The system automatically creates a unique username and a simple 6-character temporary password for each student, and lets the student choose their gender (Boy or Girl) when they first log in."
        ],
        None,
        True,
        "Inside Class Details, tap '+ Generate Students' to show the single-box student generation window with names entered."
    ),
    (
        39,
        "Student Account Management — Copy Passwords & Reset Login",
        "In the class student roster, teachers can easily copy login details for students who have not yet signed in, edit student names, or reset passwords if a student forgets their login.",
        [
            "Find a newly generated student in your roster to see their 'Unclaimed' badge, username, and temporary password.",
            "Tap the copy button next to their name to copy their login details to share with them or their parent.",
            "If a student forgets their password, tap the edit (pencil) icon on their card and tap 'Reset Password' to give them a new temporary password without losing any of their saved reading points."
        ],
        None,
        True,
        "In Class Details, show student cards with generated usernames/passwords or open the Edit Student / Reset Password window."
    ),
    (
        40,
        "Student Profile Photo Approval & Teacher Remarks",
        "When students upload a profile picture from their phone, teachers can review and approve it. Teachers can also write encouraging reading notes for individual learners.",
        [
            "Review any student profile pictures waiting for approval at the top of your class screen and tap 'Approve' or 'Reject'.",
            "Tap any student in the roster and select 'Add Teacher Remark' to write an encouraging progress note or reading observation for the student."
        ],
        None,
        True,
        "In Class Details, open the 'Add Teacher Remark' window or show a student photo review card."
    ),
    (
        41,
        "Set Up Class Introductory Reading Check (Initiation Diagnostic)",
        "Teachers can set up an introductory reading check for newly enrolled students by selecting a reading passage PDF and choosing how many comprehension questions to ask.",
        [
            "Inside your class screen, tap the 'Initiation Trial / Diagnostic Settings' button.",
            "Choose how many reading questions you want new students to answer (e.g., 5 or 10 questions).",
            "Tap 'Upload Diagnostic PDF' to select a reading passage from your phone so the system can generate comprehension questions for your students."
        ],
        None,
        True,
        "In Class Details, tap 'Initiation Trial' settings to show the introductory reading check setup window."
    ),
    (
        42,
        "Create Weekly Classroom Quiz (Select Reading PDF & Question Style)",
        "Teachers can create weekly reading quizzes directly from their class screen by attaching a lesson PDF and choosing a question style (such as Reading Comprehension or Vocabulary Clues).",
        [
            "In your class screen, tap '+ Create Weekly Assessment'.",
            "Enter the Quiz Title, choose the Question Style and number of questions, and tap 'Select Reading PDF' to choose your weekly lesson file.",
            "Tap 'Generate & Assign Assessment' to create the questions and publish the quiz to all students in your class."
        ],
        None,
        True,
        "In Class Details, tap '+ Create Weekly Assessment' to show the weekly quiz creation window."
    ),
    (
        43,
        "Review Student Quiz Scores & Submissions",
        "Once students complete a weekly reading quiz, teachers can tap the quiz card in their class details screen to see each student's score and completion time.",
        [
            "In your class details screen, tap 'Review Submissions' on an active or completed quiz card.",
            "Browse the list of submitted students to see their scores, percentages, and submission dates.",
            "Identify students who scored well or those who need additional help with the lesson."
        ],
        None,
        True,
        "In Class Details, tap 'Review Submissions' on a weekly quiz card to show the student score review window."
    ),
    (
        44,
        "Teacher Library — Subject Folders",
        "The Teacher Library organizes all your instructional reading PDFs and question pools into neat, color-coded subject folders with quick search.",
        [
            "From the Teacher Home Dashboard, tap 'Teacher Library'.",
            "Use the search box at the top to quickly find a subject folder.",
            "Tap the '+ New Subject Folder' button to create a new folder (e.g., 'English 10' or 'Science Reading Modules') and tap 'Save'."
        ],
        None,
        True,
        "Open 'Teacher Library' on the Teacher app to show subject folders or the '+ New Subject Folder' window."
    ),
    (
        45,
        "Subject Folder & Upload Reading PDF Module",
        "Inside a subject folder, teachers can upload lesson PDFs. The app automatically checks the file to ensure it is appropriate for students and prepares a comprehension quiz.",
        [
            "Tap any subject folder in your Teacher Library to open it.",
            "Tap '+ Upload PDF Material', select a reading lesson PDF from your phone, and type a clear title for the lesson.",
            "The app checks the material for educational suitability and automatically creates interactive multiple-choice questions for the lesson."
        ],
        None,
        True,
        "Open a subject folder on the Teacher app showing uploaded PDF materials or the Upload PDF title prompt."
    ),
    (
        46,
        "Assign Reading Modules to Classes (Select All / Clear All)",
        "Teachers can easily choose which of their class sections can read each uploaded PDF module using simple checkboxes with convenient 'Select All' and 'Clear All' buttons.",
        [
            "On any PDF lesson card in your subject folder, tap the 'Manage Access / Assign Sections' button.",
            "Check the boxes next to the classes you want to assign the reading module to, or tap 'Select All' to assign it to all your classes at once.",
            "Tap 'Save Access' to make the module immediately available on those students' phones."
        ],
        None,
        True,
        "In a subject folder, tap 'Manage Access' on a reading file to show the section assignment window with 'Select All'."
    ),
    (
        47,
        "Reading Module Quiz Overview",
        "Teachers can preview the interactive questions prepared for any uploaded reading module before students take the quiz.",
        [
            "In your subject folder, tap the 'Quiz Pool / Overview' button on any reading file card.",
            "Review the list of multiple-choice questions and check the total question count.",
            "Tap 'Edit Questions' if you want to modify questions or add your own custom questions."
        ],
        None,
        True,
        "Tap 'Quiz Pool / Overview' on a reading module card in the Teacher Library to show the quiz preview."
    ),
    (
        48,
        "Interactive Quiz Editor",
        "The Quiz Editor gives teachers full freedom to change question wording, edit answer choices A through D, set the correct answer, or add new questions.",
        [
            "From the quiz overview screen, tap 'Edit Quiz' to open the Quiz Editor.",
            "Scroll through the question cards to edit question text, answer options, or which choice is marked correct.",
            "Tap '+ Add Question' at the bottom to add your own question, then tap 'Save Quiz Pool' to save your changes."
        ],
        None,
        True,
        "Open the Quiz Editor showing editable question cards, options A through D, and the 'Save' button."
    ),
    (
        49,
        "Read Principal & Supervisor Notes on Lesson Materials",
        "When your School Principal or Division Supervisor leaves an instructional note or suggestion on a lesson module, you can read it directly inside the subject folder.",
        [
            "Open your subject folder and look for the 'Admin Remarks' badge on any lesson card.",
            "Tap the 'Remarks' button to open the note window and read the helpful guidance or praise sent by your school leaders."
        ],
        None,
        True,
        "In a subject folder, tap the 'Remarks' icon on a reading file card to show the administrative note window."
    ),
    (
        50,
        "Class Reading Performance & Export to Excel/CSV",
        "The Student Performance screen displays real-time class reading scores, shows comparisons between boys and girls, and lets teachers download a class grade spreadsheet with one tap.",
        [
            "From the Teacher Home Dashboard, tap 'Student Performance'.",
            "Filter by class section or by gender (Male or Female) and use the search bar to find any student.",
            "Inspect class reading averages and reading streaks, or tap 'Export CSV' to save a spreadsheet file ready for your DepEd class records."
        ],
        None,
        True,
        "Open 'Student Performance' on the Teacher app showing class reading scores and the 'Export CSV' button."
    ),
    (
        51,
        "Individual Student Reading Profile Sheet",
        "Tapping any student in your class roster opens their personal reading summary, showing their points, reading streak, reading level, and recent quiz scores.",
        [
            "In your class roster or performance screen, tap on any student's card.",
            "View their reading level badge, total points earned, active reading streak, and list of completed quizzes."
        ],
        None,
        True,
        "Tap a student's name in your class roster to show their personal reading summary card."
    ),
    (
        52,
        "Teacher Profile, Dark Theme & Mobile Recycle Bin",
        "The Teacher Profile screen lets teachers update their photo, switch between Light and Dark display themes, check for app updates, and recover deleted classes or reading files from the 30-day Recycle Bin.",
        [
            "Tap the 'Profile' tab at the bottom of the screen.",
            "Toggle 'Dark Mode' on or off to change the screen appearance, or tap 'Check for Updates' to verify you have the latest app version.",
            "Tap 'Recycle Bin / Archive Vault' to view and restore any class, student, or reading file you may have deleted within the last 30 days."
        ],
        None,
        True,
        "Open the Teacher Profile tab and show the profile screen or the 'Recycle Bin / Archive Vault' window."
    ),
]

STUDENT_MOBILE_FIGURES = [
    (
        53,
        "Student Sign-In Screen",
        "Students log in using the simple username and 6-character temporary password given to them by their classroom teacher—no personal email address required.",
        [
            "Open the GabAI app on your phone (it automatically opens to the '🎓 Student' tab).",
            "Type your teacher-assigned username in the 'Username' box.",
            "Type your temporary password in the 'Password' box and tap 'Sign In ➔'. If you forget your password, your teacher or class adviser can easily give you a new one."
        ],
        r"manual_assets\screenshots\mobile\fig_20_student_sign_in.png",
        True,
        "Show the Student Sign-In screen on the mobile app with the Username and Password boxes."
    ),
    (
        54,
        "First-Time Login: Set Password & Choose Gender",
        "The very first time a student logs in, the app asks them to create a new private password and select their gender (Boy or Girl) so their reading achievements are properly recorded in school reports.",
        [
            "Sign in with your temporary password to see the 'Secure Your Account' screen.",
            "Type your new permanent password and re-enter it to confirm.",
            "Under 'Select Your Gender', tap either '👦 Male (Boy)' or '👧 Female (Girl)'.",
            "Tap 'Update & Continue' to save your new password and gender and enter GabAI."
        ],
        None,
        True,
        "Show the first-time login screen with the New Password boxes and the '👦 Male (Boy)' / '👧 Female (Girl)' buttons."
    ),
    (
        55,
        "Profile Gender Confirmation Prompt",
        "If an existing student account has not yet selected their gender, a friendly prompt appears when opening the app so they can choose with a single tap.",
        [
            "If the 'Complete Your Profile' prompt appears, tap either '👦 Male (Boy)' or '👧 Female (Girl)'.",
            "Tap 'Save & Continue' to proceed directly to your Home screen."
        ],
        None,
        True,
        "Show the gender selection prompt on the student mobile app."
    ),
    (
        56,
        "Student Welcome Walkthrough Slides",
        "New students are greeted by three friendly introduction slides showing how to scan words, use the floating helper, and complete reading challenges.",
        [
            "Swipe left through the three welcome slides to see how the camera scanner, floating helper, and daily quests work.",
            "Tap 'Get Started' on the last slide to open your Student Home screen."
        ],
        None,
        True,
        "Show the welcome walkthrough slides on the student mobile app."
    ),
    (
        57,
        "Student Home Dashboard",
        "The Student Home screen displays your current reading Level, earned points (XP), daily reading streak flame, school announcements, and quick buttons to scan or read.",
        [
            "Check your greeting, Level, points bar, and daily reading streak at the top of the Home screen.",
            "Read any new announcements sent by your teacher or principal in the notice banner.",
            "Tap the big buttons to start scanning printed words with the Camera, turn on the Floating Assistant bubble, or open your Reading Library."
        ],
        None,
        True,
        "Show the Student Home Dashboard with the Level/XP bar, reading streak counter, and action cards."
    ),
    (
        58,
        "Join a Class via 6-Character Code or QR Scanner",
        "Students can join a teacher's class by typing the 6-character Class Code or by scanning their teacher's QR code using the built-in scanner.",
        [
            "On the Student Home screen, tap the 'Join Class' button.",
            "Type the 6-character Class Code given by your teacher and tap 'Join Class', OR tap 'Scan QR Code' and point your phone camera at your teacher's screen."
        ],
        None,
        True,
        "On the Student Home screen, tap 'Join Class' to show the Class Code input and 'Scan QR Code' button."
    ),
    (
        59,
        "Class Introductory Reading Check",
        "When joining a class that has an introductory reading check, students can read a short passage and answer a few practice questions to help their teacher understand their reading level.",
        [
            "Tap the 'Start Reading Check' card on your Home screen.",
            "Read the short story or passage carefully and answer each comprehension question.",
            "Tap 'Submit' at the end to finish your introductory check."
        ],
        None,
        True,
        "Show the introductory reading check screen with the reading passage and questions."
    ),
    (
        60,
        "Apprentice Reading Missions (8 Guided Steps)",
        "New students complete 8 simple practice missions (such as trying the floating bubble, scanning a word, and saving a favorite) to learn how to use every reading tool.",
        [
            "Tap the 'Apprentice Quests' card on your Home screen to see your 8 beginner missions.",
            "Tap any mission to read what to do and tap 'Go Now' to jump straight to that feature.",
            "Complete all 8 missions to graduate to the Daily Quests hub and unlock bonus rewards."
        ],
        None,
        True,
        "Tap the Apprentice Quests card on the Student Home screen to show the 8 beginner missions."
    ),
    (
        61,
        "Division Timed Reading Assessment (Phil-IRI Pre-Test & Post-Test)",
        "When the Division Office or your School Principal schedules an official Phil-IRI reading assessment, an alert card appears on your Home screen with a clear countdown timer.",
        [
            "Tap the active 'Division Timed Reading Test' card on your Home screen.",
            "Read the test instructions and tap 'Begin' to start the timer.",
            "Read the passage, tap your answers for each question, and tap 'Submit' before the timer runs out to record your score."
        ],
        None,
        True,
        "Show the active timed reading test screen with the reading passage, countdown timer, and question card."
    ),
    (
        62,
        "Weekly Classroom Quiz",
        "When your teacher assigns a weekly reading quiz, you can take it directly on your phone with clear questions and protection so your answers are not lost if you accidentally exit.",
        [
            "Tap your 'Weekly Classroom Quiz' card on the Home screen.",
            "Read the story and answer each multiple-choice question.",
            "Tap 'Submit Quiz' on the last question to see your score and earn reading points."
        ],
        None,
        True,
        "Show an active weekly classroom quiz question on the student mobile app."
    ),
    (
        63,
        "Camera Text Scanner Viewfinder",
        "The Camera Text Scanner lets students point their phone camera at any printed sign, textbook page, or worksheet, with a framing guide and a flashlight button for dark rooms.",
        [
            "Tap the 'Scan Text / Camera' button on your Home screen.",
            "Hold your phone steady so the printed words appear inside the rectangular framing guide.",
            "Tap the flashlight icon if you need extra light, then tap the round camera button to scan the words."
        ],
        None,
        True,
        "Show the camera scanner screen with the framing guide, flashlight button, and shutter button."
    ),
    (
        64,
        "Word Highlight Boxes on Scanned Page",
        "After taking a picture, the app highlights recognized words in neat boxes so students can simply tap any unfamiliar word to learn what it means.",
        [
            "Look at the highlighted boxes placed over each word on your captured photo.",
            "Tap on any word box that you want explained to open the Word Explanation screen."
        ],
        None,
        True,
        "Show the scanned photo with highlighted word boxes placed over the printed text."
    ),
    (
        65,
        "Smart Word Meaning, Audio Pronunciation & Pictures",
        "The Word Explanation screen provides an easy-to-understand definition, clear spoken pronunciation, helpful pictures, and lets you switch between English and Tagalog explanations.",
        [
            "Read the friendly definition and sample sentence for the selected word.",
            "Tap the speaker button to hear the word pronounced aloud in clear audio.",
            "Tap the 'Tagalog' or 'English' button to switch languages, swipe through the pictures showing what the word means, and tap the heart icon to save it to your Favorites."
        ],
        None,
        True,
        "Show the Word Explanation screen with the definition, speaker pronunciation button, language switch, and pictures."
    ),
    (
        66,
        "Floating Reading Assistant Bubble",
        "The floating GabAI bubble stays comfortably on the side of your phone screen while you read any e-book, website, or document, letting you look up words with a single tap.",
        [
            "Tap 'Activate Floating Assistant' on your Home screen to show the floating GabAI bubble.",
            "Open any reading app, web article, or classroom PDF—the bubble stays gently on the screen edge.",
            "Whenever you see an unfamiliar word, tap the bubble to scan the page and tap the word to hear its meaning without leaving your book."
        ],
        None,
        True,
        "Show the phone screen with the floating GabAI assistant bubble resting over reading text."
    ),
    (
        67,
        "Student Reading Library — Subject Folders",
        "The Student Library organizes all your classroom reading subjects into neat folders with an instant search bar.",
        [
            "Tap 'Library' on the bottom navigation menu.",
            "Use the search box to find a subject folder.",
            "Tap any subject folder to see the reading modules assigned by your teacher."
        ],
        None,
        True,
        "Show the Student Library screen with assigned subject folders and search bar."
    ),
    (
        68,
        "Class Reading Modules List",
        "Inside each subject folder, students can see all reading lessons assigned by their teacher along with completion badges.",
        [
            "Tap a subject folder in your Library to open it.",
            "Browse through the lesson cards to find the module you want to read.",
            "Tap on any lesson card to open it in the built-in reader."
        ],
        None,
        True,
        "Show the list of assigned reading modules inside a subject folder."
    ),
    (
        69,
        "Built-In Classroom PDF Reader",
        "The built-in reader displays lesson PDFs smoothly with pinch-to-zoom, page numbers, the floating reading helper bubble, and a button to take the module quiz when finished.",
        [
            "Pinch and scroll through your lesson pages inside the reader.",
            "If you see a difficult word, tap the floating reading helper bubble to look it up immediately.",
            "When you finish reading the lesson, tap the 'Take Module Quiz' button at the bottom."
        ],
        None,
        True,
        "Show an open lesson PDF inside the reader with document pages and reader controls."
    ),
    (
        70,
        "Lesson Module Reading Quiz",
        "After reading a lesson module, students can take an interactive comprehension quiz to test their understanding and earn points.",
        [
            "Tap 'Take Module Quiz' to start the quiz for your reading lesson.",
            "Read each question, tap your answer choice, and tap 'Next'.",
            "At the end of the quiz, review your score and celebrate the points (XP) you earned."
        ],
        None,
        True,
        "Show a module quiz question card or the final quiz score celebration screen."
    ),
    (
        71,
        "Vocabulary Recall Review Game",
        "The Vocabulary Recall quiz is a fun practice game that turns your scanned words and saved favorites into a fast-paced memory review to build word retention.",
        [
            "Tap 'Vocabulary Practice' on your Home screen.",
            "Read the clue and choose the correct matching word before the timer expires to build your winning combo.",
            "Finish the practice round to earn bonus points and maintain your daily streak."
        ],
        None,
        True,
        "Show the Vocabulary Recall review game screen with a question card and combo counter."
    ),
    (
        72,
        "Daily Reading Quests & Rewards Hub",
        "Every day, students receive fresh reading and scanning challenges that award bonus points and keep reading habits strong.",
        [
            "Tap 'Daily Quests' on your Home screen.",
            "Check your progress bars for scanning words, reading lesson modules, and completing quizzes.",
            "Tap the 'Claim Reward' button on any finished quest to collect your bonus points."
        ],
        None,
        True,
        "Show the Daily Quests screen with quest progress bars and 'Claim Reward' buttons."
    ),
    (
        73,
        "Student Reading Progress & Growth Dashboard",
        "The Progress screen lets students see how much their reading has grown, showing their total points, mastered words, quiz accuracy, and reading streaks.",
        [
            "Tap 'Progress' on the bottom menu.",
            "Check your overall reading score, total points, and number of words scanned.",
            "Tap 'View Quiz History' to review your past quiz scores."
        ],
        None,
        True,
        "Show the Student Reading Progress dashboard with points, reading level, and streak stats."
    ),
    (
        74,
        "Past Quiz History & Question Review",
        "Students can look back at every quiz they have ever taken and see which questions they got right or wrong, complete with helpful explanations.",
        [
            "On your Progress screen, tap 'Quiz History'.",
            "Browse the list of completed quizzes and tap on any quiz to open it.",
            "Review each question to see your chosen answer, the correct answer, and an explanation of why."
        ],
        None,
        True,
        "Show the Quiz History list or the answer review window showing correct and incorrect answers."
    ),
    (
        75,
        "Saved Words (Favorites) & Recent Scan History",
        "Students can easily revisit any word they have bookmarked or scanned, with one-tap access to word definitions and pronunciations.",
        [
            "Tap 'Favorites' to view your bookmarked vocabulary words and tap any word to reopen its meaning and sound.",
            "Tap 'Scan History' to see a list of words and signs you have recently scanned."
        ],
        None,
        True,
        "Show the Favorites or Scan History screen with saved vocabulary words."
    ),
    (
        76,
        "Class Reading Leaderboard & Achievement Badges",
        "The Leaderboard celebrates top readers in your class by points and reading streaks, while Achievement Badges reward milestones like reading 10 days in a row.",
        [
            "Tap 'Leaderboard' to see top readers in your class section.",
            "Tap 'Badges / Achievements' to see all the colorful reading badges you have unlocked and see which ones you can earn next."
        ],
        None,
        True,
        "Show the Class Leaderboard or the Achievement Badges showcase on the student app."
    ),
    (
        77,
        "Student Profile, Avatar Photo & Dark Theme",
        "The Profile screen shows your student details, lets you upload a profile picture (reviewed by your teacher), and lets you switch to Dark Mode for comfortable nighttime reading.",
        [
            "Tap 'Profile' on the bottom menu.",
            "Tap your profile picture circle to choose a photo from your phone (your teacher will approve it).",
            "Turn on 'Dark Mode' if you prefer a dark screen while reading at night, or tap 'Sign Out' when using a shared device."
        ],
        None,
        True,
        "Show the Student Profile screen with the student's name, username, points, and Dark Mode switch."
    ),
    (
        78,
        "Help Center, Guides & App Updates",
        "The Help section provides simple step-by-step guides for students and teachers, and lets you easily check for new app updates.",
        [
            "On your Profile screen, tap 'Help & Support' to browse answers to common questions.",
            "Tap 'Check for App Updates' to see if a newer version of GabAI is available and update with a single tap."
        ],
        None,
        True,
        "Show the Help & Support screen or the App Update window on the mobile app."
    ),
]


# ==============================================================================
# 4. MAIN DOCUMENT BUILDER (PLAIN LANGUAGE)
# ==============================================================================

def build_gabai_manual():
    doc = Document()

    # Configure 1.0-inch (72 pt) margins on all 4 sides
    for section in doc.sections:
        section.top_margin = Inches(1.0)
        section.bottom_margin = Inches(1.0)
        section.left_margin = Inches(1.0)
        section.right_margin = Inches(1.0)
        section.different_first_page_header_footer = True
        add_page_number_footer(section)

    # Default Normal style
    normal_style = doc.styles['Normal']
    normal_style.font.name = 'Times New Roman'
    normal_style.font.size = Pt(12)
    normal_style.font.color.rgb = RGBColor(0x22, 0x22, 0x22)

    # --------------------------------------------------------------------------
    # COVER PAGE
    # --------------------------------------------------------------------------
    for _ in range(4):
        doc.add_paragraph()

    p_cov1 = doc.add_paragraph()
    p_cov1.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_cov1 = p_cov1.add_run("SYSTEM & USER MANUAL")
    r_cov1.font.name = 'Times New Roman'
    r_cov1.font.size = Pt(26)
    r_cov1.bold = True
    r_cov1.font.color.rgb = RGBColor(0x11, 0x11, 0x11)

    add_horizontal_rule(doc, color="5341CD", sz="18")

    p_cov2 = doc.add_paragraph()
    p_cov2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_cov2.paragraph_format.space_before = Pt(18)
    p_cov2.paragraph_format.space_after = Pt(4)
    r_cov2 = p_cov2.add_run("GabAI:")
    r_cov2.font.name = 'Times New Roman'
    r_cov2.font.size = Pt(24)
    r_cov2.bold = True
    r_cov2.font.color.rgb = RGBColor(0x53, 0x41, 0xCD)

    p_cov3 = doc.add_paragraph()
    p_cov3.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_cov3 = p_cov3.add_run(
        "AI-Powered Optical Sign Recognition &\n"
        "Classroom Reading Intelligence Platform"
    )
    r_cov3.font.name = 'Times New Roman'
    r_cov3.font.size = Pt(15)
    r_cov3.bold = True
    r_cov3.font.color.rgb = RGBColor(0x33, 0x33, 0x33)

    for _ in range(6):
        doc.add_paragraph()

    p_meta = doc.add_paragraph()
    p_meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_meta = p_meta.add_run(
        "GabAI Official System & User Manual\n"
        "Schools Division Office of Valenzuela City (DepEd SDO Valenzuela)\n"
        "Comprehensive Step-by-Step User Manual"
    )
    r_meta.font.name = 'Times New Roman'
    r_meta.font.size = Pt(11)
    r_meta.italic = True
    r_meta.font.color.rgb = RGBColor(0x55, 0x55, 0x55)

    doc.add_page_break()

    # --------------------------------------------------------------------------
    # TABLE OF CONTENTS
    # --------------------------------------------------------------------------
    add_heading_1(doc, "Table of Contents")
    add_body_p(
        doc,
        "Right-click the Table of Contents field below in Microsoft Word and select 'Update Field -> Update entire table' to refresh page numbers automatically after inserting your screenshots."
    )
    add_toc_field(doc)

    toc_summary = [
        ("1. Overview", "1.1 System Overview  |  1.2 Target Users  |  1.3 Key Features"),
        ("2. System Requirements", "2.1 Hardware Requirements  |  2.2 Software Requirements  |  2.3 Network & Permissions"),
        ("3. Installation & Setup Guide", "3.1 Mobile App Installation & Updates  |  3.2 Web Administrative Portal Access"),
        ("4. User Manual (78 Figures)", ""),
        ("   4.1 Web Admin Portal — Division Super Admin (SDO)", "Figures 1 – 19 (Administrator Setup, Division Reading Overview, School Directory, Announcements, 30-Day Recovery Bin, Teacher & Section Review, Student Accounts, Division Reading Tests, and System Setup)"),
        ("   4.2 Web Admin Portal — School Administrator", "Figures 20 – 30 (School Account Activation, School Reading Overview, School Bulletins, Teacher Approvals, Class Sections, Student Reading Scores, Password Resets, Reading Tests, Teacher Feedback, and School Recovery Bin)"),
        ("   4.3 Mobile Application — Teacher / Educator Role", "Figures 31 – 52 (Teacher Registration, Teacher Home Screen, Announcements, Class Details, One-Box Student Account Creation, Introductory Reading Checks, Weekly Quizzes, Teacher Library, Reading Module Uploads, Quiz Editor, Class Reading Performance, and Recovery Vault)"),
        ("   4.4 Mobile Application — Student / Learner Role", "Figures 53 – 78 (Student Login, Password & Gender Setup, Welcome Slides, Student Home Screen, Join a Class, Reading Check, Beginner Missions, Division Timed Tests, Weekly Quizzes, Camera Text Scanner, Word Meanings & Audio, Floating Helper Bubble, PDF Reader, Module Quizzes, Memory Games, Daily Quests, Reading Progress, and Student Profile)")
    ]
    for sec_title, sec_desc in toc_summary:
        add_bullet_p(doc, f"{sec_title}: ", sec_desc)

    doc.add_page_break()

    # --------------------------------------------------------------------------
    # SECTION 1: OVERVIEW
    # --------------------------------------------------------------------------
    add_heading_1(doc, "1. Overview")

    add_heading_2(doc, "1.1 System Overview")
    add_body_p(
        doc,
        "GabAI is a comprehensive reading companion and classroom learning platform created for public Junior High Schools in the Schools Division of Valenzuela City. The system brings together an easy-to-use mobile application for teachers and students with an online school management website for school principals and division supervisors. Using smart camera text recognition and helpful artificial intelligence, GabAI helps students instantly understand unfamiliar words from textbooks, printed modules, and digital materials, while giving educators clear insights into reading performance and progress."
    )
    add_body_p(
        doc,
        "Through its connected design, GabAI allows students to scan printed words on signs, books, and handouts using either their phone camera or a convenient floating reading helper bubble that stays on screen over any digital document. When a student selects an unfamiliar word, the app provides clear explanations in English and Filipino, spoken audio pronunciation, visual pictures, and quick practice questions. At the same time, teachers, school principals, and division leaders receive real-time reading assessment results (including official Phil-IRI reading tiers and DepEd School Form 1 reports separated by boys and girls) to support targeted reading intervention."
    )

    add_heading_2(doc, "1.2 Target Users")
    add_bullet_p(
        doc,
        "Division Super Administrators (SDO Valenzuela): ",
        "Division-level education leaders and program supervisors who oversee reading programs across all participating Junior High Schools in Valenzuela City. Division administrators manage the official school list, assign school principals, schedule uniform division-wide reading assessments (Phil-IRI Pre-Tests and Post-Tests), broadcast official memorandums, monitor division-wide reading scores, manage the 30-day Recycle Bin, and manage system settings."
    )
    add_bullet_p(
        doc,
        "School Administrators (Principals, Department Heads & Coordinators): ",
        "School leaders who oversee reading activities for their specific campus through the online management website. School administrators review and approve newly registered teachers, monitor classroom sections and class codes, check school reading mastery levels, identify students who need reading intervention, reset forgotten student passwords, send instructional feedback on reading modules, and restore school records from the Recycle Bin."
    )
    add_bullet_p(
        doc,
        "Teachers / Educators (Classroom Teachers & Homeroom Advisers): ",
        "Faculty members who use the teacher section of the mobile app to manage class sections, generate student accounts in one easy step by copying names from attendance sheets, approve student profile photos, configure introductory reading assessments, upload lesson PDF reading modules, assign reading access, edit interactive quizzes, schedule weekly reading checks, post class bulletins, and download student reading reports."
    )
    add_bullet_p(
        doc,
        "Students / Learners: ",
        "Junior High School students who use the student section of the mobile app with a teacher-assigned username and password (no personal email required). Upon logging in for the first time, students set their private password and select their gender (Boy or Girl) for school records. Students can complete beginner practice missions, scan text with the camera or floating helper bubble, read classroom PDF modules, take weekly and timed reading quizzes, and earn points, reading badges, and daily reading streaks."
    )

    add_heading_2(doc, "1.3 Key Features")
    add_bullet_p(
        doc,
        "Camera Text Scanner & Smart Word Explanations: ",
        "Students can point their phone camera at any printed book, sign, or worksheet to instantly see friendly word definitions, switch between English and Filipino explanations, hear clear spoken pronunciations, and view helpful pictures."
    )
    add_bullet_p(
        doc,
        "Floating Reading Assistant Bubble: ",
        "A convenient, movable floating icon that stays on the side of the phone screen while students read any e-book, website, or document, letting them look up difficult words with a single tap without closing their reading material."
    )
    add_bullet_p(
        doc,
        "Four Practical Reading Assessment Modes: ",
        "Supports (1) Division-wide timed reading tests (Phil-IRI Pre-Tests and Post-Tests with clear countdown timers and growth comparisons), (2) Class introductory reading checks, (3) Weekly classroom reading quizzes created by teachers, and (4) Lesson module quizzes and fun vocabulary recall review games."
    )
    add_bullet_p(
        doc,
        "Simple One-Box Student Setup & Gender Profile: ",
        "Teachers can paste an entire class attendance list into a single text box to create student accounts with temporary passwords in seconds. On their first login, students select their gender (Boy or Girl) and set their private password, ensuring accurate DepEd school reports."
    )
    add_bullet_p(
        doc,
        "Organized School Bulletins & 30-Day Recovery Bin: ",
        "Division and school leaders can post announcements with read receipts, and all deleted classes, reading files, or student records are safely protected in a 30-day Recycle Bin so nothing is lost by accident."
    )

    doc.add_page_break()

    # --------------------------------------------------------------------------
    # SECTION 2: SYSTEM REQUIREMENTS
    # --------------------------------------------------------------------------
    add_heading_1(doc, "2. System Requirements")

    add_heading_2(doc, "2.1 Hardware Requirements")
    add_body_p(
        doc,
        "Table 1 outlines the minimum and recommended hardware specifications for running the GabAI mobile application and the online administrative website."
    )
    add_styled_table(
        doc,
        ["Device / Component", "Minimum Specification", "Recommended Specification"],
        [
            ["Mobile Phone Processor", "Standard smartphone processor (Quad-Core or better)", "Fast multi-core smartphone processor"],
            ["Mobile Memory (RAM)", "3 GB RAM", "4 GB RAM or more (recommended for smooth reading with the floating assistant)"],
            ["Phone Storage Space", "150 MB free phone storage", "500 MB free phone storage (for downloaded reading modules)"],
            ["Phone Camera & Screen", "Standard rear camera with autofocus; standard touchscreen", "Clear high-resolution camera and touchscreen display"],
            ["Computer (for Principals & SDO)", "Standard office desktop or laptop computer (4 GB RAM)", "Modern desktop or laptop computer (8 GB RAM, high-definition monitor)"]
        ]
    )

    add_heading_2(doc, "2.2 Software Requirements")
    add_body_p(
        doc,
        "Table 2 details the operating system, web browser, and file format requirements for using the GabAI system."
    )
    add_styled_table(
        doc,
        ["System Component", "Minimum Requirement", "Recommended Standard"],
        [
            ["Mobile Phone Operating System", "Android 7.0 or newer", "Android 9.0 or newer (compatible up to Android 15)"],
            ["Web Browser (for Principals & SDO)", "Any modern web browser (Google Chrome, Edge, Firefox, Safari)", "Latest version of Google Chrome or Microsoft Edge"],
            ["Internet Connection", "Basic Wi-Fi or mobile data connection", "Reliable Wi-Fi or broadband connection for downloading reading modules"],
            ["Reading Material Format", "Standard PDF documents", "Standard classroom lesson and worksheet PDFs"]
        ]
    )

    add_heading_2(doc, "2.3 Network and Permission Requirements")
    add_body_p(
        doc,
        "Table 3 lists the simple phone permissions required by GabAI to scan printed text, show the floating reading helper, and save student reading progress."
    )
    add_styled_table(
        doc,
        ["Permission / Feature", "Setting in Phone", "Why It Is Needed in GabAI"],
        [
            ["Camera Access", "Camera Permission", "Needed to scan printed book pages, signs, and classroom QR codes."],
            ["Display Over Other Apps", "Floating Window / Appear on Top", "Allows the helpful floating reading assistant bubble to stay on screen while reading books or web articles."],
            ["Screen Reading Helper", "Accessibility / Screen Reading", "Allows the floating reading bubble to read and explain text on the screen when you tap it."],
            ["Internet Access", "Wi-Fi or Mobile Data", "Used to look up word meanings, download teacher lesson materials, and sync reading progress."],
            ["Gentle Touch Feedback", "Touch Vibration", "Provides subtle, pleasant vibration feedback when selecting answers during quizzes."]
        ]
    )

    doc.add_page_break()

    # --------------------------------------------------------------------------
    # SECTION 3: INSTALLATION & SETUP GUIDE
    # --------------------------------------------------------------------------
    add_heading_1(doc, "3. Installation & Setup Guide")

    add_heading_2(doc, "3.1 Mobile Application Installation & In-App Self-Updater")
    add_body_p(
        doc,
        "Follow the simple step-by-step instructions below to download, install, and open the GabAI mobile application on a student or teacher phone."
    )

    install_steps = [
        (
            "Step 1: Open the Official GabAI Website",
            "Open your web browser on your phone or computer and visit the official GabAI website.",
            r"manual_assets\screenshots\install\install_step1_website.png",
            False
        ),
        (
            "Step 2: Download the GabAI App",
            "Tap the blue 'Download GabAI App' button on the homepage to download the installation file to your device.",
            r"manual_assets\screenshots\install\install_step2_download.png",
            False
        ),
        (
            "Step 3: Open the Downloaded File",
            "Open your phone's Downloads or Files folder, tap on the downloaded GabAI file, and tap 'Install'. If prompted by your phone, tap 'Allow' to proceed.",
            r"manual_assets\screenshots\install\install_step4_downloads.png",
            True
        ),
        (
            "Step 4: Open GabAI and Allow Permissions",
            "Tap the GabAI icon on your phone screen to open the app. When prompted, allow Camera and Floating Window permissions so the scanner and reading bubble can assist you. Future updates can be installed with a single tap inside the app.",
            r"manual_assets\screenshots\install\install_step7_launcher.png",
            True
        ),
    ]

    for st_title, st_desc, st_img, is_mob in install_steps:
        add_body_p(doc, f" — {st_desc}", bold_prefix=st_title)
        if os.path.exists(st_img):
            p_i = doc.add_paragraph()
            p_i.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_i.paragraph_format.space_before = Pt(4)
            p_i.paragraph_format.space_after = Pt(10)
            p_i.add_run().add_picture(st_img, width=Inches(2.25 if is_mob else 5.2))

    add_heading_2(doc, "3.2 Web Administrative Portal Access & Role Access Guide")
    add_body_p(
        doc,
        "GabAI is designed with clear roles so that administrators, teachers, and students have tools tailored specifically for their needs. Table 4 summarizes how each user accesses the system."
    )
    add_styled_table(
        doc,
        ["User Role", "Where to Access", "How to Sign In / Activate", "Main Responsibilities"],
        [
            ["Division Super Admin (SDO)", "Online Administrative Website", "Division Master Password setup", "Division-wide oversight of all Junior High Schools, division reading assessments, and official announcements"],
            ["School Administrator", "Online Administrative Website", "School Admin Account or Official School Invite Code", "Managing assigned school campus, approving teachers, student accounts, and school reading reports"],
            ["Teacher / Educator", "GabAI Mobile App (Educator Portal)", "Registration in mobile app with approval from School Principal", "Managing classes, creating student accounts, uploading reading modules, and assigning quizzes"],
            ["Student / Learner", "GabAI Mobile App (Student Portal)", "Teacher-provided username and temporary password", "Scanning words, using the floating reading assistant, reading modules, and taking quizzes"]
        ]
    )

    doc.add_page_break()

    # --------------------------------------------------------------------------
    # SECTION 4: USER MANUAL (78 FIGURES ACROSS ALL 4 ROLES)
    # --------------------------------------------------------------------------
    add_heading_1(doc, "4. User Manual — Step-by-Step Feature & Module Guide")
    add_body_p(
        doc,
        "This section provides an easy-to-follow, screen-by-screen guide for all 78 screens and features across the GabAI platform, organized into four clear parts: (4.1) Web Administrative Portal for Division Super Administrators, (4.2) Web Administrative Portal for School Administrators, (4.3) Mobile App Guide for Teachers, and (4.4) Mobile App Guide for Students."
    )

    # PART 4.1: SUPER ADMIN
    add_heading_2(doc, "4.1 Web Administrative Portal — Division Super Admin (SDO Valenzuela)")
    add_body_p(
        doc,
        "The Division Super Admin portal provides centralized oversight across all participating Junior High Schools in Valenzuela City, including school directory management, assigning school leaders, scheduling division-wide reading assessments, broadcasting announcements, and reviewing reading growth."
    )
    for fig_tuple in SUPER_ADMIN_FIGURES:
        add_figure_block(doc, *fig_tuple)

    doc.add_page_break()

    # PART 4.2: SCHOOL ADMIN
    add_heading_2(doc, "4.2 Web Administrative Portal — School Administrator (Principal / School IT)")
    add_body_p(
        doc,
        "The School Administrator portal focuses specifically on your assigned school campus, providing tools to verify teachers, organize classroom sections, assist with student accounts, review lesson materials, and track school reading improvement."
    )
    for fig_tuple in SCHOOL_ADMIN_FIGURES:
        add_figure_block(doc, *fig_tuple)

    doc.add_page_break()

    # PART 4.3: TEACHER MOBILE APP
    add_heading_2(doc, "4.3 Mobile Application — Teacher / Educator Role")
    add_body_p(
        doc,
        "The GabAI Educator Mobile App helps teachers manage Grade 7 to 10 class sections, create student accounts in one easy step by copying names from attendance sheets, configure introductory reading assessments, upload lesson PDFs, edit quizzes, and monitor class reading scores."
    )
    for fig_tuple in TEACHER_MOBILE_FIGURES:
        add_figure_block(doc, *fig_tuple)

    doc.add_page_break()

    # PART 4.4: STUDENT MOBILE APP
    add_heading_2(doc, "4.4 Mobile Application — Student / Learner Role")
    add_body_p(
        doc,
        "The GabAI Student Mobile App provides students with a friendly reading companion featuring simple login, gender selection for school records, practice missions, camera word scanning, the floating reading helper bubble, spoken pronunciations, lesson modules, and interactive reading quizzes."
    )
    for fig_tuple in STUDENT_MOBILE_FIGURES:
        add_figure_block(doc, *fig_tuple)

    output_path = r"c:\Users\plaza\Documents\GitHub\OSRnary\GabAI_System_and_User_Manual.docx"
    doc.save(output_path)
    print(f"SUCCESS: Generated {output_path} ({os.path.getsize(output_path):,} bytes)")

if __name__ == "__main__":
    build_gabai_manual()
