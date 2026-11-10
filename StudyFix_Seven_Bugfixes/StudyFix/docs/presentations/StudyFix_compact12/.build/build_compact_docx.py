from pathlib import Path
import re

here = Path(__file__).parent
template = here.parents[1] / 'StudyFix_corrected' / '.build' / 'build_speech_docx.py'
source = template.read_text(encoding='utf-8')
source = source.replace('28', '12').replace('speech_content.json', 'content.json')
source = source.replace('StudyFix_Project_Overview_12_slides_Chuan_Source.pptx', 'StudyFix_Tong_Quan_12_Slide.pptx')
source = re.sub(r"for a, b in \[.*?\]:", "for a, b in [('01-02', content[0]['title']), ('03-07', content[2]['title'] + ' va giao thuc'), ('08-10', content[7]['title']), ('11-12', content[10]['title'])]:", source)
# Reuse the verified document style and questions; new content and output live in this task directory.
exec(compile(source, str(here / 'generated_compact_builder.py'), 'exec'), {'__file__': str(here / 'generated_compact_builder.py'), '__name__': '__main__'})
