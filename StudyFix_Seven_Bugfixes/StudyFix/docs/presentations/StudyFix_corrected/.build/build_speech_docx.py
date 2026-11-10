from pathlib import Path
import json
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT

HERE = Path(__file__).parent
OUT = HERE.parent / 'output' / 'StudyFix_Loi_Thuyet_Trinh_28_Slide.docx'
content = json.loads((HERE / 'speech_content.json').read_text(encoding='utf-8'))
assert len(content) == 28
doc = Document()
sec = doc.sections[0]
sec.page_width, sec.page_height = Inches(8.5), Inches(11)
sec.top_margin, sec.bottom_margin = Inches(.72), Inches(.7)
sec.left_margin, sec.right_margin = Inches(.78), Inches(.78)
sec.header_distance, sec.footer_distance = Inches(.29), Inches(.3)
for name in ['Normal', 'Title', 'Subtitle', 'Heading 1', 'Heading 2', 'Heading 3', 'Header', 'Footer']:
    st = doc.styles[name]
    st.font.name = 'Arial'
    st.font.color.rgb = RGBColor(0, 0, 0)
    rpr = st.element.get_or_add_rPr()
    rf = rpr.find(qn('w:rFonts'))
    if rf is not None:
        for attr in ['asciiTheme', 'hAnsiTheme', 'eastAsiaTheme', 'cstheme']:
            rf.attrib.pop(qn('w:' + attr), None)
        rf.set(qn('w:eastAsia'), 'Arial')
    ppr = st.element.find(qn('w:pPr'))
    if ppr is not None:
        border = ppr.find(qn('w:pBdr'))
        if border is not None: ppr.remove(border)
normal = doc.styles['Normal']
normal.font.size = Pt(11.5)
normal.paragraph_format.line_spacing = 1.08
normal.paragraph_format.space_after = Pt(5)
normal.paragraph_format.widow_control = True
doc.styles['Title'].font.size = Pt(25)
doc.styles['Title'].font.bold = True
doc.styles['Title'].paragraph_format.space_after = Pt(14)
doc.styles['Subtitle'].font.size = Pt(12)
for name, size in [('Heading 1', 16), ('Heading 2', 12.5), ('Heading 3', 11.5)]:
    doc.styles[name].font.size = Pt(size)
    doc.styles[name].font.bold = True
    doc.styles[name].paragraph_format.space_before = Pt(11)
    doc.styles[name].paragraph_format.space_after = Pt(6)
    doc.styles[name].paragraph_format.keep_with_next = True

header = sec.header.paragraphs[0]
header.text = 'STUDYFIX  |  LỜI THUYẾT TRÌNH THEO SLIDE'
header.style = 'Header'
for r in header.runs: r.font.size = Pt(9)
footer = sec.footer.paragraphs[0]
footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
def field(paragraph, instruction):
    run = paragraph.add_run()
    begin = OxmlElement('w:fldChar'); begin.set(qn('w:fldCharType'), 'begin')
    txt = OxmlElement('w:instrText'); txt.set(qn('xml:space'), 'preserve'); txt.text = ' ' + instruction + ' '
    end = OxmlElement('w:fldChar'); end.set(qn('w:fldCharType'), 'end')
    run._r.extend([begin, txt, end])
footer.add_run('Trang '); field(footer, 'PAGE'); footer.add_run(' / '); field(footer, 'NUMPAGES')
for r in footer.runs: r.font.size = Pt(9)

def para(t, label=None, small=False):
    p = doc.add_paragraph()
    if label:
        p.add_run(label + ' ').bold = True
    r = p.add_run(t)
    if small:
        for x in p.runs: x.font.size = Pt(10.5)
        r.italic = True
    return p

def heading(t, new_page=False, level=1):
    p = doc.add_heading(t, level)
    p.paragraph_format.page_break_before = new_page
    return p

doc.add_paragraph('Lời thuyết trình dự án StudyFix', 'Title')
doc.add_paragraph('Tài liệu đọc và tập trình bày theo bộ 28 slide đã đối chiếu source', 'Subtitle')
para('Nhóm ........................................    Lớp ........................................')
para('Giảng viên ................................................................................')
para('Ngày biên soạn 04/11/2026. Dùng cùng file StudyFix_Project_Overview_28_slides_Chuan_Source.pptx.')
heading('Cách dùng tài liệu', level=2)
para('Mỗi mục tương ứng một slide và có lời đọc chính, câu chuyển ý, cùng ghi nhớ kỹ thuật. Khi báo cáo, đọc hoặc diễn đạt lại lời chính và câu chuyển ý; phần ghi nhớ dùng để chuẩn bị, không cần đọc thành tiếng.')
para('Giọng trình bày dùng “nhóm em”. Người nói tự thay tên, cách xưng hô và phân công cho phù hợp. Các ví dụ mô tả nghiệp vụ, không phải số liệu sử dụng thật. Tập đọc cùng slide trước buổi báo cáo để điều chỉnh theo thời gian giảng viên cho phép.')
heading('Gợi ý chia phần', level=2)
t = doc.add_table(rows=1, cols=3)
t.alignment = WD_TABLE_ALIGNMENT.CENTER
t.autofit = False
widths = [Inches(.75), Inches(3.65), Inches(2.5)]
for c, w in zip(t.columns, widths): c.width = w
for c, txt in zip(t.rows[0].cells, ['Slide', 'Nội dung', 'Người trình bày']): c.text = txt
for a, b in [('01–06', 'Bài toán và nhóm chức năng'), ('07–12', 'Kiến trúc server đăng nhập và phòng'), ('13–16', 'Master nhánh và Merge'), ('17–23', 'Bài tập chấm code media và dữ liệu'), ('24–28', 'Triển khai kiểm thử và kết luận')]:
    row = t.add_row().cells
    row[0].text, row[1].text, row[2].text = a, b, '........................'
for ri, row in enumerate(t.rows):
    trpr = row._tr.get_or_add_trPr()
    if ri == 0:
        repeat = OxmlElement('w:tblHeader'); trpr.append(repeat)
    no_split = OxmlElement('w:cantSplit'); trpr.append(no_split)
    for ci, cell in enumerate(row.cells):
        cell.width = widths[ci]
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        tcpr = cell._tc.get_or_add_tcPr()
        borders = OxmlElement('w:tcBorders')
        for side in ['top', 'left', 'bottom', 'right']:
            edge = OxmlElement('w:' + side); edge.set(qn('w:val'), 'single'); edge.set(qn('w:sz'), '4'); edge.set(qn('w:color'), 'D9D9D9'); borders.append(edge)
        tcpr.append(borders)
        margins = OxmlElement('w:tcMar')
        for side in ['top', 'left', 'bottom', 'right']:
            edge = OxmlElement('w:' + side); edge.set(qn('w:w'), '90'); edge.set(qn('w:type'), 'dxa'); margins.append(edge)
        tcpr.append(margins)
        shade = OxmlElement('w:shd'); shade.set(qn('w:fill'), 'E8EEF3' if ri == 0 else ('FFFFFF' if ri % 2 else 'F6F8FA')); tcpr.append(shade)
        for p in cell.paragraphs:
            p.paragraph_format.space_after = Pt(2); p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.line_spacing = 1.05
            if ci != 1: p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            for r in p.runs: r.font.size = Pt(10.5); r.bold = ri == 0
para('Có thể gộp hoặc tách các phần theo số thành viên thực tế. Không bắt buộc một phần ứng với một người.', small=True)

for i, item in enumerate(content):
    heading(f'Slide {i+1:02d} {item["title"]}', new_page=(i % 2 == 0))
    for paragraph in item['paragraphs']: para(paragraph)
    para(item['transition'], 'Chuyển ý')
    para(item['reminder'], 'Ghi nhớ khi nói', small=True)

qas = [
('Vì sao chỉ dùng web mà vẫn cần TCP 5000', 'Gateway web hiện đóng vai trò một client TCP nội bộ để đăng nhập và chuyển các lệnh lõi tới Server và ClientHandler. Browser chỉ gọi HTTP hoặc HTTPS tới gateway. Bỏ ứng dụng desktop Swing không tự loại bỏ được đường TCP nội bộ này.'),
('Web có gửi yêu cầu bài tập qua cổng 5002 không', 'Trong luồng web hiện tại, gateway gọi hàm handleWebRequest của AssignmentTcpServer ngay trong cùng JVM rồi vào AssignmentService. Cổng 5002 vẫn là listener cho giao thức JSON TCP, nhưng không phải đường browser trực tiếp dùng để nộp bài.'),
('Tại sao header packet màn hình phải cộng thêm một', 'Packet native có một byte loại trước payload. Header cho biết tổng số byte phần thân nên bằng một cộng độ dài payload. Nếu chỉ ghi độ dài payload, bên nhận có thể đọc thiếu một byte và làm lệch các frame sau.'),
('Merge có phải Git merge và có bắt buộc AC không', 'Không phải Git three-way merge. Giáo viên chọn một nhánh để dùng toàn bộ source của nhánh đó làm Master mới. Backend kiểm tra chủ phòng, phase, vòng và phiên bản, nhưng case MERGE hiện tại không bắt buộc nhánh đã AC.'),
('Học sinh AC rồi tại sao Master chưa thay đổi', 'AC là kết quả kiểm tra snapshot nhánh riêng. Giáo viên còn phải FREEZE, xem nhánh và chủ động chọn Merge. Vì vậy nhánh đúng có thể đã được lưu mà Master vẫn giữ nguyên cho tới khi giáo viên chọn lời giải.'),
('Hai tab cùng sửa một nhánh thì xử lý thế nào', 'Mỗi request lưu mang expectedVersion. Tab lưu trước làm version tăng. Tab còn lại gửi version cũ sẽ bị từ chối để tránh ghi đè im lặng. Người dùng cần đồng bộ và giữ lại nháp cần thiết trước khi chọn dùng bản server.'),
('Chạy thử khác nộp chính thức ở đâu', 'Trong Practice, chạy thử chỉ dùng public tests và không tạo thành tích AC chính thức. Nộp chính thức tạo version theo user và bài, chấm theo bộ test của bài. Vì vậy đúng ví dụ công khai chưa bảo đảm qua các test ẩn.'),
('Mất phản hồi nộp bài có tạo hai submission không', 'Luồng Practice lưu idempotencyKey trước khi gửi. Khi retry cùng key, bài, loại thao tác và source, backend có thể trả lại submission đã tạo. Dùng cùng key cho nội dung khác bị từ chối. Cần phân biệt cơ chế này với requestId dùng để đối chiếu yêu cầu.'),
('Nếu Docker hoặc database bị lỗi thì sao', 'Thiếu Docker hoặc image sẽ trả lỗi sandbox, không tự chạy code học sinh trên host. Nếu đã có kết quả nhưng ghi database lỗi, worker giữ verdict và thử ghi lại, có log và số lượt chờ lưu. Database còn hỏng thì chưa thể cam kết đã lưu; kết quả RAM cũng không được bảo đảm sống qua process crash.'),
('Server có truyền toàn bộ camera và mic không', 'Backend hiện quản lý signaling, roster và kiểm tra membership. Media của web đi qua các kết nối WebRTC giữa browser peers. Source chưa có SFU hoặc TURN riêng; việc hoạt động trên một mạng không chứng minh mọi mạng khác đều kết nối được.'),
('Rời cuộc họp có phải rời phòng không', 'Rời cuộc họp dừng media và quay về màn hình chính, còn membership được giữ. Rời phòng là thao tác riêng để tách thành viên khỏi phòng. Cần dùng đúng hai tên khi hướng dẫn hoặc demo.'),
('Đã chứng minh hệ thống hỗ trợ bao nhiêu người chưa', 'Các giới hạn cấu hình không phải kết quả đo tải. Hiện có kiểm thử chức năng backend và Docker, nhưng chưa có số liệu benchmark để khẳng định số người đồng thời, p95 hay throughput. Giới hạn 16 thiết bị là của cuộc gọi trong một phòng, không phải kết luận về khả năng chịu tải toàn hệ thống.')
]
for i, (q, a) in enumerate(qas):
    if i % 6 == 0: heading('Câu hỏi thường gặp' if i == 0 else 'Câu hỏi thường gặp tiếp theo', new_page=True)
    heading(f'Câu {i+1:02d} {q}', level=2)
    para(a)

heading('Tra nhanh trước buổi trình bày', new_page=True)
heading('Các thuật ngữ cần nói đúng', level=2)
for term, explanation in [
    ('Master', 'Code chung trong phiên giảng dạy.'),
    ('Branch', 'Nhánh code riêng của học sinh trong một vòng thực hành.'),
    ('Snapshot', 'Bản chụp nội dung tại một thời điểm để lưu hoặc chấm đúng version.'),
    ('Queue và worker', 'Hàng đợi công việc và luồng lấy công việc ra xử lý.'),
    ('Signaling', 'Trao đổi thông tin để thiết lập WebRTC, khác luồng media.'),
    ('AC và DONE', 'AC là đạt bộ test; DONE chỉ là đã hoàn tất xử lý.'),
    ('Session và user', 'Session là phiên kết nối hoặc truy cập; user là tài khoản.'),
    ('RESUME và MERGE', 'RESUME về BRANCHING; MERGE về LECTURE.')
]: para(explanation, term)
heading('Các câu cần tránh', level=2)
para('Không khẳng định hệ thống dùng JWT, WebSocket, Git merge hoặc SFU nếu source không triển khai. Không nói mọi draft chỉ nằm trong sessionStorage, mọi bài AC tự lên Master, hoặc toàn bộ browser E2E và media LAN đã được nghiệm thu.')
heading('Tài liệu đối chiếu', level=2)
para('Bộ slide đi kèm có ghi chú từng slide và đường dẫn source. Khi cần mở code, dùng docs/PHAN_TICH_CHI_TIET_DU_AN_WEB_SERVER_VI.md, docs/SERVER_MANAGER_VI.md và docs/SERVER_RELIABILITY_FIXES_VI.md trong dự án.')
para('Bằng chứng kiểm thử được nhắc ở slide 25 nằm trong server/build/server-reliability-full-regression.log và server/build/server-reliability-docker.log. Kiểm tra lại source và ngày chạy nếu dự án đã thay đổi sau ngày biên soạn.')
doc.core_properties.title = 'Lời thuyết trình dự án StudyFix'
doc.core_properties.subject = 'Lời đọc và hỏi đáp theo bộ 28 slide'
doc.core_properties.author = ''
doc.core_properties.keywords = 'StudyFix thuyết trình web server TCP Master Branch Merge Docker'
OUT.parent.mkdir(parents=True, exist_ok=True)
doc.save(OUT)
print('Created', OUT)
print('Slides', len(content), 'Questions', len(qas), 'Paragraphs', len(doc.paragraphs))
